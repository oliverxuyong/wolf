package so.xunta.websocket.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import so.xunta.websocket.utils.WeChatUtils;

public class WeChatShareLinksUtils {
	private Logger logger = Logger.getRootLogger();
	private final String apiTicketUrl= "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=ACCESS_TOKEN&type=jsapi";
	private WeChatUtils wsGetAccessToken=new WeChatUtils();
	
	public JSONObject makeWXTicket(String url) {
		JSONObject ret = new JSONObject();
		String nonceStr = createNonceStr();
		String timestamp = createTimestamp();
		String string1;
		String signature = "";
		String jsApiTicket=getJsApiTicket();

		// 注意这里参数名必须全部小写，且必须有序
		string1 = "jsapi_ticket=" + jsApiTicket + "&noncestr=" + nonceStr + "&timestamp=" + timestamp + "&url=" + url;
		logger.debug("String1=====>" + string1);
		try {
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(string1.getBytes("UTF-8"));
			signature = byteToHex(crypt.digest());
			logger.debug("signature=====>" + signature);
		} catch (NoSuchAlgorithmException e) {
			logger.error("WeChatController.makeWXTicket=====Start");
			logger.error(e.getMessage());
			logger.error("WeChatController.makeWXTicket=====End");
		} catch (UnsupportedEncodingException e) {
			logger.error("WeChatController.makeWXTicket=====Start");
			logger.error(e.getMessage());
			logger.error("WeChatController.makeWXTicket=====End");
		}

		//ret.put("url", url);
		//ret.put("jsapi_ticket", jsApiTicket);
		ret.put("nonceStr", nonceStr);
		ret.put("timestamp", timestamp);
		ret.put("signature", signature);

		return ret;
	}
	
	//获取ticket
	private String getJsApiTicket() {
	   String accessToken = wsGetAccessToken.getToken("wxdac88d71df6be268", "753b50cf29b6b08e733e357cc0ed348c");  //微信凭证，access_token
	   String requestUrl = apiTicketUrl.replace("ACCESS_TOKEN", accessToken);
	   // 发起GET请求获取凭证
	   String jsonStr=wsGetAccessToken.httpsRequest(requestUrl, "GET", null);
	   JSONObject jsonObject =new JSONObject(jsonStr);
	   return jsonObject.getString("ticket");
	}
	
	//字节数组转换为十六进制字符串
	private String byteToHex(final byte[] hash) {
	    Formatter formatter = new Formatter();
	    for (byte b : hash)
	    {
	        formatter.format("%02x", b);
	    }
	    String result = formatter.toString();
	    formatter.close();
	    return result;
	}
	//生成随机字符串
	private String createNonceStr() {
	    return UUID.randomUUID().toString();
	}
	//生成时间戳
	private String createTimestamp() {
	    return Long.toString(System.currentTimeMillis() / 1000);
	}

}	
