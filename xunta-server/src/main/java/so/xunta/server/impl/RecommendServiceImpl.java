package so.xunta.server.impl;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Tuple;
import so.xunta.beans.ConcernPointDO;
import so.xunta.beans.User;
import so.xunta.persist.C2uDao;
import so.xunta.persist.ConcernPointDao;
import so.xunta.persist.CpChoiceDetailDao;
import so.xunta.persist.U2cDao;
import so.xunta.persist.U2uRelationDao;
import so.xunta.persist.U2uUpdateStatusDao;
import so.xunta.persist.UserDao;
import so.xunta.persist.UserLastUpdateTimeDao;
import so.xunta.persist.impl.C2uDaoIml;
import so.xunta.server.RecommendService;
import so.xunta.utils.RecommendTaskPool;

@Service
public class RecommendServiceImpl implements RecommendService {
	@Autowired
	private C2uDao c2uDao;
	@Autowired
	private U2cDao u2cDao;
	@Autowired
	private U2uRelationDao u2uRelationDao;
	@Autowired
	private U2uUpdateStatusDao u2uUpdateStatusDao;
	@Autowired
	private ConcernPointDao concernPointDao;
	@Autowired
	private UserLastUpdateTimeDao userLastUpdateTimeDao;
	@Autowired
	private CpChoiceDetailDao cpChoiceDetailDao;
	@Autowired
	private UserDao userDao;
	
	Logger logger =Logger.getLogger(C2uDaoIml.class);
	
	private final double NO_CHANGE = 0.0;
	
	/**
	 * @author Bright_Zheng
	 * 
	 * 1.得到与用户U答过同一题的用户列表：  
	 *通过C2U表得到选过相同C的用户列表{Ui}，并将U加到C中
	  *2.更新U2U_Update_Status
	  *遍历{Ui}，在U2U_Update_Status表中U的关系用户列表里对Ui的∆u_score值加上刚选中CP的score值，没有则新增
	  *同时也在Ui的关系用户列表里找到U，将其∆u_score值加上刚选中CP的score值，没有则新增。
	  *3.记录状态改变
	  *通过U2U_Relation表得到所有和U有关系的用户{Uj},在U2U_Update_Status表中为每个Uj记录上U（增加的∆u_score为0）
	 * */
	@Override
	public void recordU2UChange(String uid, String cpid, int selectType) {
		
		logger.info("线程 "+Thread.currentThread().getName()+" recordU2UChange begin:"+" uid: "+
						uid+"\t cpid: "+cpid+"\t selectType: "+selectType);
		long startTime = System.currentTimeMillis();
	
		//新增时先查后更新C2U，删除时先更新后查C2U,这样在后面遍历时就不会把自己也包括进去
		Set<String> usersSelectedSameCp = null;
		if(selectType == RecommendService.SELECT_CP){
			usersSelectedSameCp= c2uDao.getUsersSelectedSameCp(cpid);
			c2uDao.saveCpOneUser(cpid, uid);
		}else{
			c2uDao.deleteUserInCp(cpid, uid);
			usersSelectedSameCp= c2uDao.getUsersSelectedSameCp(cpid);
		}
		Double dValue = concernPointDao.getConcernPoint(new BigInteger(cpid)).getWeight().doubleValue();
	
		for(String relatedUid:usersSelectedSameCp){
			switch(selectType){
			case RecommendService.SELECT_CP:
				u2uUpdateStatusDao.updateDeltaRelationValue(uid, relatedUid, dValue);
				u2uUpdateStatusDao.updateDeltaRelationValue(relatedUid, uid, dValue);
				break;
			case RecommendService.UNSELECT_CP:
				u2uUpdateStatusDao.updateDeltaRelationValue(uid, relatedUid, -dValue);
				u2uUpdateStatusDao.updateDeltaRelationValue(relatedUid, uid, -dValue);
				break;
			}
		}
		
		Set<Tuple> relatedUsers=u2uRelationDao.getRelatedUsersByRank(uid, 0, -1);//0表示第一个，-1为倒数第一个，即为获取所有关系用户
		Set<String> relatedUids = new HashSet<String>();
		for(Tuple user:relatedUsers){
			relatedUids.add(user.getElement());
		}
		relatedUids.removeAll(usersSelectedSameCp);//产生了∆u_score的User不需要重复记录
		
		for(String relatedUid:relatedUids){
			final double UPDATE_MARK = 0;
			u2uUpdateStatusDao.updateDeltaRelationValue(relatedUid, uid, UPDATE_MARK);
		}
		long endTime = System.currentTimeMillis();
		logger.info("线程 "+Thread.currentThread().getName()+" recordU2UChange end:"+"\t 选中相同CP的用户数: "+ 
						usersSelectedSameCp.size() +"\t 其他产生推荐的用户数: "+ relatedUsers.size() +"\n 执行时间: "+
						(endTime-startTime)+"毫秒");
	}
	
	/**
	 * @author Bright Zheng
	 *  
	 *1.在U2U_Update_Status中获取U所需要更新的状态发生过变化的用户集合{Uj}。
	 *2. 遍历{Uj}，对每个Uj
	 	*2.1更新U2U_Relation
	 	如∆u_score不为0，在U2U_Relation中，为U对应的Uj的关系值加上对应在U2U_Update_Status中的∆u_score
	 	*2.2更新U2C
		如∆u_score为0，则得到Uj在U的update_time后更新的标签列表{CPi}，
			对每个CPi，在U的U2C表中对CPi的推荐分score 加上（ CPi自身的score * U-Uj的关系值u_score），新增为正，取消为负。
		如∆u_score不为0，则得到Uj在update_time之前的已答标签列表{CPj}以及update_time后更新的标签列表{CPz}。
			对每个CPj，在U的U2C表中对CPj的推荐分score 加上（ CPi自身的score * U-Uj的∆u_score）
			对每个CPz，在U的U2C表中对CPj的推荐分score 加上（ CPi自身的score * U-Uj的u_score）。
	 *3.将U在U2U_Update_Status中的记录删除，将U的update_time更新为当前时间。
	 * */
	@Override
	public void updateU2C(String uid) {
		logger.info("线程 "+Thread.currentThread().getName()+" updateU2C begin:"+"\t uid: "+ uid);
		long startTime = System.currentTimeMillis();
		
		Map<String,String> userUpdateStatusMap= u2uUpdateStatusDao.getUserUpdateStatus(uid);
		logger.info("上次更新后有"+userUpdateStatusMap.size()+"个相关用户有了新状态");
		Timestamp lastUpdateTime = Timestamp.valueOf(userLastUpdateTimeDao.getUserLastUpdateTime(uid));
		
		for(Entry<String,String> changedUserEntry:userUpdateStatusMap.entrySet()){
			String changedUid = changedUserEntry.getKey();
			double uDeltaValue = Double.valueOf(changedUserEntry.getValue());
			Map<BigInteger, String> newCps= cpChoiceDetailDao.getSelectedCpAfterTime(Long.valueOf(changedUid), lastUpdateTime);
			
			if(Math.abs(uDeltaValue - NO_CHANGE) < 1e-6){
				updateU2CAfterLastUpdated(newCps, uid, changedUid);
			}else{
				u2uRelationDao.updateUserRelationValue(uid, changedUid, uDeltaValue);
				//推荐CP列表中已选的CP值为一个很大的负数，就算再加也不会产生影响
				updateU2CAfterLastUpdated(newCps, uid, changedUid);
				updateU2CBeforeLastUpdated(uid, Long.valueOf(changedUid), lastUpdateTime, uDeltaValue);
			}
		}
		
		u2uUpdateStatusDao.deleteU2uUpdateStatus(uid);
		userLastUpdateTimeDao.setUserLastUpdateTime(uid, new Timestamp(System.currentTimeMillis()).toString());
		long endTime = System.currentTimeMillis();
		
		logger.info("线程 "+Thread.currentThread().getName()+" updateU2C end: 更新完毕"+ uid + 
					"\n 执行时间: "+(endTime-startTime)+"毫秒");
	}

	/**
	 * 用户每次上线的初始化任务，包括
	 * 将last updated time存入redis, 触发一次更新任务
	 * */
	@Override
	public void initRecommendParm(User u) {
		logger.info("用户: "+ u.getName()+" 开始初始化推荐参数");
		Timestamp lastUpdateTime = u.getLast_update_time();
		userLastUpdateTimeDao.setUserLastUpdateTime(u.getUserId().toString(), lastUpdateTime.toString());
		Boolean ifInited = u2cDao.ifUserCpInited(u.getUserId().toString());
		if(!ifInited){
			logger.info("用户: "+ u.getName()+" U2C列表不存在,初始化列表:");
			
			final Long SYSTEM_ADMIN = 1L; 
			List<ConcernPointDO> initCps = concernPointDao.listConcernPointsByCreator(SYSTEM_ADMIN, 0, 10000);
			Map<String,Double> initCpsMap = new HashMap<String,Double>();
			for(ConcernPointDO cp:initCps){
				String cpId = cp.getId().toString();
				Double cpWeight = cp.getWeight().doubleValue();
				initCpsMap.put(cpId, cpWeight);
			}
			u2cDao.updateUserBatchCpValue(u.getUserId().toString(), initCpsMap);
			
			logger.info("用户: "+ u.getName()+" U2C列表初始化成功！");
		}
		
		logger.info("初始化推荐参数完成，执行一次更新任务");
		RecommendTaskPool.getInstance().getThreadPool().execute(new Runnable() {	
			@Override
			public void run() {
				updateU2C(u.getUserId().toString());
			}
		});
	}

	/**
	 * 将用户的lastUpdateTime从Redis同步到数据库中
	 * */
	@Override
	public void syncLastUpdateTime(User u) {
		logger.info("用户: "+ u.getName()+" 下线，将更新时间同步到数据库");
		Timestamp lastUpdateTime = Timestamp.valueOf(userLastUpdateTimeDao.getUserLastUpdateTime(u.getUserId().toString()));
		u.setLast_update_time(lastUpdateTime);
		userDao.updateUser(u);
	}
	
	private void updateU2CAfterLastUpdated(Map<BigInteger, String> newCps, String uid, String changedUid){
		logger.info("CP新选更新：用户 "+changedUid);
		for(Entry<BigInteger, String> selectedCp:newCps.entrySet()){
			BigInteger selectedCpid = selectedCp.getKey();
			String is_selected = selectedCp.getValue();
			Double cpWeight = concernPointDao.getConcernPoint(selectedCpid).getWeight().doubleValue();
			Double relateScore = u2uRelationDao.getRelatedUserScore(uid, changedUid);
			
			if(is_selected.equals(CpChoiceDetailDao.SELECTED)){
				u2cDao.updateUserCpValue(uid, selectedCpid.toString(), cpWeight*relateScore);
			}else{
				 //负值是取消标签导致，正常情况下取消前肯定有选中过程，所以总的值不会为负
				u2cDao.updateUserCpValue(uid, selectedCpid.toString(), -cpWeight*relateScore);
			}
		}
	}
	
	private void updateU2CBeforeLastUpdated(String uid,Long changedUid,Timestamp lastUpdateTime,double uDeltaValue){
		logger.info("CP已选更新：用户 "+changedUid);
		List<BigInteger> oldCps = cpChoiceDetailDao.getSelectedCpBeforeTime(changedUid, lastUpdateTime);
		for(BigInteger oldCp:oldCps){
			Double cpWeight = concernPointDao.getConcernPoint(oldCp).getWeight().doubleValue();
			u2cDao.updateUserCpValue(uid, oldCp.toString(), cpWeight*uDeltaValue);
		}
	}
}
