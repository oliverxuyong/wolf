//返回上一页   2016/12/25 deng
function backBtn(){
	if(_topicPageSign == 'yes'){
		execRoot("setCurrentPageId('main_page')");
		//exec('topics_page',"removeUnreadNum('"+topicId+"')");
		openWin('main_page', 'main_page/main_page.html', '');
	}else{
		closeWin(_tmpPageId);
	}

	//execRoot("closeWin("+_tmpPageId+")");
	//closeWin(_tmpPageId);
}

//关闭当前页，返回主界面   2016/12/25 deng
function closeBtn(){
	execRoot("setCurrentPageId('main_page')");
	//exec('main_page',"removeUnreadNum('"+topicId+"')");
	openWin('main_page', 'main_page/topics_page.html', '');
	closeWin(_tmpPageId);
}