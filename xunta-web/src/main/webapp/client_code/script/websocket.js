var ws_obj = 'init';
//var ws_status = "null";
var firstMsgId;
//用来记录话题分页消息的界点，
var sort;
//用来记录获取消息分页的排序方式，第一次是升序，第二次是降序，这与显示消息有关
var currentMsgIdArray = new Array();

var requestMsgCounts;//它的值是从dialog.html里传过来的,然后供其它方法使用.
//记录客户端接受到的消息ID，用来查重，只记录即时会话消息，不记录获取历史消息
//json格式的用户信息
//function set_userId(userid) {
//	userId = userid;
//	console.log("12345");
//}

window.requestTopicNum = '20';//话题列表分页记录变量-一次获取的数量
var lastTopicTime = '-1';//话题列表分页记录变量-一次获取的时间节点

function set_lastTopicTime(lasttopictime) {
	lastTopicTime = lasttopictime;
}

var doRequestCP = false;
var requestCPNum; //每次请求话题数//这两个变量由mainpage在请求cp时传过来,在这里保存,供执行任务筐的任务时使用.xu
var currentRequestedCPPage; //这两个变量由mainpage在请求cp时传过来,在这里保存,供执行任务筐的任务时使用.xu


//请求话题列表的任务筐. xu9.9
var doRequestPostHist = new Array();
//请求历史滔滔息的任务筐.
var doSendPoster = new Array();
//发送消息的任务筐.角标用"话题id-临时pid"来识别. 内容存为json数据,里面存放请求相关数据.
var doRequestCreateNewTopic = new Array();
//请求创建新话题的任务筐;
var doRequestMoveToNewTopic = new Array();
//请求移动创建新话题的任务筐.json数据,存放请求相关数据.

/*
 var topicIndex = 0;//定义话题关系数据的角标  9.16 FANG 指定排到第几了.
 var topicIdArray = new Array();//创建话题和临时话题ID的对应关系  9.16 FANG
 var tempTopicIdArray = new Array();//创建话题和临时话题ID的对应关系  9.16 FANG
 */
var topicId2tmpTopicId = new Array();
//用于替代上面三个变量,效率会更高.角标为topicid,值为tmptopicid. xu 10.25

var tmptid2inputvalue = new Array();
//临时存放原始数据,供执行任务筐时使用.
var tmptid2tmppid = new Array();
//临时存放原始数据,供执行任务筐时使用.

var currentPageId = null;


function initToGetCP(userId,requestNum,currentPage) {
	requestCPNum = requestNum;
	currentRequestedCPPage = currentPage;
	//$("#loadinganimation").css("display", "block");
	//$(".login_container").css("display", "none");
	doRequestCP = true;	//任务筐中登记.
	//先添加响应任务,同时作为请求进行中的状态标志.
	console.log("initToGetCP...userId=" + userId);
	//$("#loadinganimation").click(initToGetTopicList(userId));//有时该方法会在请求失败时点击拖拉机来调用,所以这里先取消点击事件.
	if (checkIfWSOnline4topiclist()) {//如果ws处于连接状态,直接发出请求. 如果没有连接,该方法会发出创建请求.
		requestCP();
	}
	/*
	if (lastTopicTime == '-1') {//如果是第一次请求,需要返回到index.html里执行一次打开页面的操作.
		initOpenMainPage();//该方法在index.html里.xu
	} else {//如果话题列表页已经打开,则直接将数据显示出来:
		//exec('showTopicList','topics_page',evnt.data);
		exec("main_page","showCP()");
		//document.getElementById("topics_page").contentWindow.showTopicList(evnt.data);
	}
	*/
}

function task_RequestCP() {//检查并执行话题列表请求任务.
	if (doRequestCP) {//检查并执行 请求话题列表的任务.xu9.9
		console.log("CP请求任务为true,现在执行请求...");
		requestCP();
	}
}

function requestCP(){//请一组CP.首次请求页号设为1.
	console.log("真正请求一组CP:,userId="+userId+" 请求数量="+requestCPNum+" 请求的页面="+currentRequestedCPPage);
	var json_obj = {
			 _interface:"1101-1",
			 interface_name: "requestCP",
			 uid:userId,
			 startpoint: currentRequestedCPPage,
			 howmany:requestCPNum,//每次请求多少个标签
			 timestamp:	"",//暂时无用.
		};
	WS_Send(json_obj);
}

function tasksOnWired() {//ws连接事件的响应执行方法:
	console.log("网络通了,现在执行任务筐.");
	task_RequestCP();
	//检查并执行话题列表请求任务.

}




function showSetupInfo(eventdata) {
	var jsonObj = JSON.parse(eventdata);
	var pagename = getTmpTopicIdIfExisted(jsonObj.topicid);
	if (pagename == jsonObj.topicid) {
		logging("出错消息,用于发现潜在的代码错误. 一般用户不必理会.(新话题成功后的后续通知消息没有找到临时页面的tmptopicid.可能有网络不稳定使成功创建消息晚于该后续消息.也可能是断网期间用户关闭了创建页.)");
	}
	console.log("showSetupInfo() - pagename=" + pagename);
	console.log(jsonObj.private + "|||" + jsonObj.suspend);
	exec(pagename, "showSetupBox(" + eventdata + ")");
}


function getSetupInfo(topicId) {
	console.log("getSetupInfo 发了. topicId=" + topicId);
	var json_obj = {
		_interface : "get_topicsetup",
		topicid : topicId
	};
	WS_Send(json_obj);
}

function setSetupInfo(topicId, boolean_private, suspend) {
	console.log("setSetupInfo 发了. private=" + boolean_private + "|suspend=" + suspend);
	//alert("本话题的新设置已经提交. 成功后应该收到一条通知消息.");
	var json_obj = {
		_interface : "set_topicsetup",
		topicid : topicId,
		private : boolean_private,
		suspend : suspend
	};
	WS_Send(json_obj);
}



function setCurrentPageId(value) {
	currentPageId = value;
	console.log("set currentPageId=" + currentPageId);
}

function clearCurrentPageId() { //由于null无法通过参数传递,专设一个clear方法:
	currentPageId = null;
	console.log("clear currentPageId=" + currentPageId);
}

/**
 *	关闭指定的聊天框页面Name
 *  */
function closeTmpWin(tmpPageName) {
	setTimeout(function() {
		api.closeWin({
			name : tmpPageName
		});
	}, 1500);
}


function WS_Send(json_obj) {//抽出这个通用发送方法,发送前都检查ws对象的有效性,防止报错.xu11.18
	if (checkIfWSOnline()) {
		ws_obj.send(JSON.stringify(json_obj));
		logging("执行WS发送.接口:" + json_obj._interface);
		console.log("执行WS发送.接口:" + json_obj._interface);
	}
}

function checkMessageInterface(evnt) {
	
	var jsonObj = JSON.parse(evnt.data);
	//把字符串转换成json对象.
	console.log("收到消息,类型=" + jsonObj._interface);
	console.log("消息内容:" + JSON.stringify(jsonObj).substring(0,150)+"...(只显示150个字符)");
	logging("收到消息,类型=" + jsonObj._interface);
	if(jsonObj._interface == '1101-2'){
		console.log(JSON.stringify(jsonObj.cp_wrap));
	}
}


function browserOnlineOfflineEvent(){
	window.ononline = function(){
		console.log("响应window.ononline事件,执行createWebSocket(no).");
		toast("网络连接中断.");
		createWebSocket("no");//create时会豪饮查是否早已on了.
	}
	window.onoffline = function(){
		console.log("响应window.onoffline事件,执行closeWebSocket().");
		closeWebSocket();//close时会检查是否早已closed.
		toast("网络连接中断.");
	}
}

function websocketEvent() {
	ws_obj.onopen = function(evnt) {
		console.log("ws连接事件, 显示在线图标. |ws_obj=" + ws_obj + " |readyState=" + ws_obj.readyState);
		logging("ws连接事件, 显示在线图标. |ws_obj=" + ws_obj + " |readyState=" + ws_obj.readyState);
		if (topicsPageOpenMark == "yes") {//没有这个判断,启动时会报错.xu1113.
			exec("main_page", "showWebsocketStatus('ws_on')");
		}else{
			console.log("ws建立,要显示在线图标,却发现列表页没有打开.topicsPageOpenMark == yes不成立.");
		}
		
		console.log("WS成功建立连接,向前台页面发送toast通知.currentPageId:" + currentPageId);
		exec(currentPageId, "toast('服务器连接已恢复.')");

		tasksOnWired();
	};
	ws_obj.onmessage = function(evnt) {
		checkMessageInterface(evnt);
	};
	ws_obj.onerror = function(evnt) {
		logging("WS出错事件. ws_obj=" + ws_obj + "|readyState=" + ws_obj.readyState + " |然后显示为离线图标");
		console.log("WS出错事件. ws_obj=" + ws_obj + "|readyState=" + ws_obj.readyState + "|然后显示为离线图标");
		if (topicsPageOpenMark == "yes") {//没有这个判断,启动时会报错.xu1113.
			exec("topics_page", "showWebsocketStatus('ws_closed')");
		}else{
			console.log("ws出错,要显示离线图标,却发现列表页没有打开.topicsPageOpenMark == yes不成立.");
		}
		
		console.log("WS因出错发生关闭,向前台页面发送toast通知.currentPageId:" + currentPageId);
		exec(currentPageId, "toast('服务器连接异常中断,请在恢复网络连接后再操作.')");
	};

	ws_obj.onclose = function(evnt) {
		logging('WS关闭事件.显示离线图标. ws_obj=' + ws_obj + "|readyState=" + ws_obj.readyState);
		console.log("WS关闭事件.显示离线图标. ws_obj=" + ws_obj + "|readyState=" + ws_obj.readyState);
		if (topicsPageOpenMark == "yes") {//没有这个判断,启动时会报错.xu1113.
			exec("topics_page", "showWebsocketStatus('ws_closed')");
		}else{
			console.log("ws关闭,要显示离线图标,却发现列表页没有打开.topicsPageOpenMark == yes不成立.");
		}

		console.log("WS关闭事件,向前台页面发送toast通知.currentPageId:" + currentPageId);
		exec(currentPageId, "toast('服务器连接已中断,请在恢复网络连接后再操作.')");
	}
}


function closeWebSocket() {
	console.log("进入closeWebSocket(). ws_obj=" + ws_obj + "|readyState=" + ws_obj.readyState);
	logging("进入closeWebSocket(). ws_obj=" + ws_obj + "|readyState=" + ws_obj.readyState);
	if (ws_obj.readyState == 2 || ws_obj.readyState == 3) {
		//ws_obj = null;
		console.log("关闭ws的时候却发现ws_obj.readyState == 2/3,不关闭了.");
		logging("关闭ws的时候却发现ws_obj.readyState == 2/3,不关闭了.");
		return;
	}
	if (ws_obj != null & ws_obj != 'init') {//关闭时间较长后,这个对象可能会成为null.
		ws_obj.close();
	}

	//console.log("已执行wsclose操作, 让ws_stauts=off, ws_obj=null");
	//logging("已执行wsclose操作,让 ws-status=off, ws_obj=null");
	console.log("已执行wsclose操作");
	logging("已执行wsclose操作");
	//ws_status = 'off';
	//ws_obj = null;
}

function checkIfWSOnline4topiclist() {
	if (ws_obj.readyState == 1) {
		console.log("checkIfWSOnline: 在线状态,返回true. ws_obj=" + ws_obj + " |readyState=" + ws_obj.readyState);
		return true;
	} else {
		console.log("checkIfWSOnline: 非在线状态,重建ws连接,返回false. ws_obj=" + ws_obj + " |readyState=" + ws_obj.readyState);
		createWebSocket("no");
        return false;
	}
}


/**
 * @param timerArr[0], timer标记
 * @param timerArr[1], 初始的title文本内容
 */
function stopFlashTitle(timerArr) {//去除闪烁提示，恢复初始title文本
	if (timerArr) {
		clearInterval(timerArr[0]);
		document.title = timerArr[1];
	}
}


