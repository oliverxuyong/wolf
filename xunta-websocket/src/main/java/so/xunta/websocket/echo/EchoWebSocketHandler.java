package so.xunta.websocket.echo;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import so.xunta.beans.User;
import so.xunta.server.LoggerService;
import so.xunta.server.RecommendService;
import so.xunta.server.UserService;
import so.xunta.utils.DateTimeUtils;
import so.xunta.utils.IdWorker;
import so.xunta.websocket.config.Constants;
import so.xunta.websocket.config.WebSocketContext;
import so.xunta.websocket.task.RecommendUpdateTask;
import so.xunta.websocket.utils.RecommendTaskPool;

/**
 * Echo messages by implementing a Spring {@link WebSocketHandler} abstraction.
 */
public class EchoWebSocketHandler extends TextWebSocketHandler {

	@Autowired
	private WebSocketContext websocketContext;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private RecommendService recommendService;
	
	@Autowired
	private LoggerService loggerService;
	
	@Autowired
	private RecommendUpdateTask recommendUpdateTask; 
	
	@Autowired
	private RecommendTaskPool recommendTaskPool;

	IdWorker idWorker = new IdWorker(1L, 1L);

	private static final Logger logger;

	private static ArrayList<WebSocketSession> users;

	public static ArrayList<WebSocketSession> getUsers() {
		return users;
	}

	public static void setUsers(ArrayList<WebSocketSession> users) {
		EchoWebSocketHandler.users = users;
	}

	private static boolean isRunning = false;

	static {
		users = new ArrayList<>();
		logger = Logger.getRootLogger();
	}

	public EchoWebSocketHandler() {
		super();
		/*if (!isRunning) {
			timer.schedule(new HeartBeatTask(), 1000, 4000);
			isRunning = true;
		}*/
		if(!isRunning){
			//timer.schedule(saveUnreadMsgTask,5000,2000);
			//isRunning = true;
			//System.out.println("saveUnreasMsgTask是否为空:"+saveUnreadMsgTask);
		}
	}

	/**
	 * 消息中央处理器
	 */
	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		String userid = session.getAttributes().get(Constants.WEBSOCKET_USERNAME).toString();
		System.out.println("客户端"+userid+"请求：" + message.getPayload());
	
	
		org.json.JSONObject obj = null;
		try {
			obj = new org.json.JSONObject(message.getPayload());
			User user = userService.findUser(Long.valueOf(userid));
			loggerService.log(userid, user.getName(),obj.toString());
		} catch (Exception e) {
			System.out.println("非json格式" + e.getMessage());
			session.sendMessage(new TextMessage("json数据格式错误"));
			return;
		}

		String _interface = obj.get("_interface").toString();
		System.out.println("_interface:" + _interface);
		websocketContext.executeMethod(_interface, session, message);
	}

	/**
	 * 建立连接
	 */
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		userOnline(session);
	}

	
	private void userOnline(WebSocketSession session) {
		Long userid  = Long.valueOf(session.getAttributes().get(Constants.WEBSOCKET_USERNAME).toString());
		if (!checkExist(session)) {
			users.add(session);
			User u = userService.findUser(userid);
			
			recommendService.initRecommendParm(u);
			recommendUpdateTask.setUid(u.getUserId()+"");
			recommendTaskPool.execute(recommendUpdateTask);
			
			/*if(session.getAttributes().get("boot").equals("yes"))
			{
				logger.info("用户:"+u.getUserId()+"  "+u.getName() +"  打开应用上线");
			}else{
				logger.info("用户"+u.getUserId()+"  "+u.getName()+"恢复连接");
				
				re_sendMsg(userid,5); //zheng 先取消，以后的更新任务还会有类似的功能
			}*/
		} else {
		}
	}
	
	@SuppressWarnings("unused")
	private void re_sendMsg(Long userid, int i) {
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("补发");
				WebSocketSession socketSession =getUserById(userid);
				if(socketSession!=null){
					websocketContext.executeMethod("submit_client_new_msg_id", socketSession, null);
				}else{
					System.out.println("opps ! session is null");
				}
			
			}
		}).start();
		
	}

	/**
	 * 连接正常关闭
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		
		userOffLine(session);
		
	}

	private void userOffLine(WebSocketSession session) {
		
		try {
			Long userid  = Long.valueOf(session.getAttributes().get(Constants.WEBSOCKET_USERNAME).toString());
			User u = userService.findUser(userid);
			recommendService.syncLastUpdateTime(u);
			
			logger.info("用户:"+u.getUserId()+"  "+u.getName() +"  离线");
			
			if (session.isOpen()) {
				session.close();
			}
		} catch (Exception e) {
			logger.error("User offLine Error: ",e);
		} finally {
			users.remove(session);
		}
	}

	/**
	 * 连接异常关闭
	 */
	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		System.out.println("连接异常");
		userOffLine(session);
	}

	/**
	 * 给所有在线用户发送消息
	 *
	 * @param message
	 */
	public static void sendMessageToUsers(TextMessage message) {
		for (WebSocketSession user : users) {
			try {
				if (user.isOpen()) {
					user.sendMessage(message);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 给所有用户发消息，但过滤掉指定的用户
	 * 
	 * @param message
	 * @param filterUserids
	 */
	public static void sendMessageToUsersExceptFilterUsers(TextMessage message, List<String> filterUserids) {

		for (WebSocketSession user : users) {
			try {
				if (user.isOpen()
						&& !filterUserids.contains(user.getAttributes().get(Constants.WEBSOCKET_USERNAME).toString())) {
					user.sendMessage(message);
				} else {
					logger.info("发消息过滤:" + user.getAttributes().get(Constants.WEBSOCKET_USERNAME));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 给某个用户发送消息
	 *
	 * @param userName
	 * @param message
	 */
	public void sendMessageToUser(String userName, TextMessage message) {

		for (WebSocketSession user : users) {
			if (user.getAttributes().get(Constants.WEBSOCKET_USERNAME).equals(userName)) {
				try {
					if (user.isOpen()) {
						user.sendMessage(message);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}
	}

	public static WebSocketSession getUserById(Long userid) {
		WebSocketSession _user=null;
		for (WebSocketSession user : users) {
			if (user.getAttributes().get(Constants.WEBSOCKET_USERNAME).toString().equals(userid.toString())) {
				if (user.isOpen()) {
					_user = user;
				}
				break;
			}
		}
		return _user;
	}

	public static boolean checkExist(WebSocketSession session) {
		for (WebSocketSession user : users) {
			if (user.getAttributes().get(Constants.WEBSOCKET_USERNAME)
					.equals(session.getAttributes().get(Constants.WEBSOCKET_USERNAME))) {
				return true;
			}
		}
		return false;
	}

	public static void removeUser(String user) {
		for (int i = 0; i < users.size(); i++) {
			WebSocketSession u = users.get(i);
			String _user = (String) (u.getAttributes().get(Constants.WEBSOCKET_USERNAME));
			if (_user.equals(user)) {
				//logger.info("用户重复连接websocket,移除原来的session" + u.getAttributes().get(Constants.WEBSOCKET_USERNAME));
				users.remove(u);
				if (u.isOpen()) {
					try {
						u.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	public static boolean checkExist(String userId) {
		for (WebSocketSession user : users) {
			if (user.getAttributes().get(Constants.WEBSOCKET_USERNAME).equals(userId)) {
				return true;
			}
		}
		return false;
	}

	@PostConstruct
	public void init() {
		try {
			if (websocketContext.getwebsocketContext().size() == 0) {
				websocketContext.scanPackage("so.xunta.websocket");
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	class HeartBeatTask extends TimerTask {

		@Override
		public void run() {
			if (users != null) {
				for (WebSocketSession user : users) {
					try {
						JSONObject ack_json = new JSONObject();
						ack_json.put("_interface", "ack");
						ack_json.put("data", "ACK:" + DateTimeUtils.getCurrentTimeStr());
						user.sendMessage(new TextMessage(ack_json.toString()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

	}
}
