package so.xunta.server.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Tuple;
import so.xunta.beans.ConcernPointDO;
import so.xunta.beans.RecommendCpBO;
import so.xunta.persist.C2uDao;
import so.xunta.persist.ConcernPointDao;
import so.xunta.persist.U2cDao;
import so.xunta.persist.impl.C2uDaoIml;
import so.xunta.server.RecommendService;
import so.xunta.server.ResponseGroupCPsService;
import so.xunta.utils.RecommendTaskPool;

@Service
public class ResponseGroupCPsSerivceImpl implements ResponseGroupCPsService {
	@Autowired
	private U2cDao u2cDao;
	
	@Autowired
	private ConcernPointDao concernPointDao;
	
	
	@Autowired
	private C2uDao c2uDao;
	
	@Autowired
	private RecommendService recommandService;
	
	Logger logger =Logger.getLogger(C2uDaoIml.class);
	
	@Override
	public List<RecommendCpBO> getRecommendCPs(Long uid, int startPoint, int howMany) {
		long startTime = System.currentTimeMillis();
		Set<Tuple> cps= u2cDao.getUserCpsByRank(uid.toString(), 0, howMany-1);
		List<String> cpIds=new ArrayList<String>();
		logger.info("得到 "+ cps.size() +" 条cp");
		
		List<RecommendCpBO> returnList = new ArrayList<RecommendCpBO>();
		for(Tuple cp:cps){
			String cpid = cp.getElement();
			//Double cpScore = cp.getScore();
			

			ConcernPointDO cpDO = concernPointDao.getConcernPoint(BigInteger.valueOf(Long.valueOf(cpid)));
			
			RecommendCpBO cpBO = new RecommendCpBO();
			cpBO.setCpId(cpid);
			cpBO.setCpText(cpDO.getText());
			cpBO.setHowManyPeopleSelected(c2uDao.getHowManyPeopleSelected(cpid));
			String if_selected = "N"; //因为选择的CP都已经被置为已推荐，因此新的一批推荐CP不会是选择过的，先这么处理
			//CpChoiceDetailDO cpChoiceDetailDO= cpChoiceDetailDao.getCpChoiceDetail(uid, BigInteger.valueOf(Long.valueOf(cpid)));					
			/*if(cpChoiceDetailDO != null){
				if_selected = cpChoiceDetailDO.getIs_selected();
			}*/
			cpBO.setIfSelectedByMe(if_selected);
			returnList.add(cpBO);
			cpIds.add(cpid);
		}
		
		u2cDao.setUserCpsPresented(uid.toString(), cpIds);
		
		RecommendTaskPool.getInstance().getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				recommandService.updateU2C(uid+"");
			}
		});
		
		long endTime = System.currentTimeMillis();
		logger.info("getRecommendCPs success，执行时间："+(startTime-endTime)+"毫秒");
		return returnList;
	}

}
