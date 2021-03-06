package so.xunta.server.impl;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import redis.clients.jedis.Tuple;
import so.xunta.beans.ConcernPointDO;
import so.xunta.beans.CpChoiceDO;
import so.xunta.beans.CpChoiceDetailDO;
import so.xunta.beans.User;
import so.xunta.persist.C2uDao;
import so.xunta.persist.ConcernPointDao;
import so.xunta.persist.CpChoiceDao;
import so.xunta.persist.CpChoiceDetailDao;
import so.xunta.persist.InitialCpDao;
import so.xunta.persist.U2cDao;
import so.xunta.persist.U2uRelationDao;
import so.xunta.persist.U2uUpdateStatusDao;
import so.xunta.persist.UserDao;
import so.xunta.persist.UserLastUpdateTimeDao;
import so.xunta.server.RecommendService;

@Service
public class RecommendServiceImpl implements RecommendService {
	private final double NO_CHANGE = 0.0;
	private final Double INIT_CP_SCORE = 0.1;//CP初始化推荐分数
	private final double SELF_ADD_CP_SCORE = 0.5;//用户自己添加cp的初始化推荐分数
	private final int REPLENISH_NUM = 100;//每次补充多少个CP
	private final double UPDATE_MARK = 0; //需要更新但用户关系值没变化
	private final long MIN_INTERVAL = 1500L; //两次更新任务之间的最短间隔时间
	
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
	private CpChoiceDao cpChoiceDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private InitialCpDao initialCpDao;

	Logger logger =Logger.getLogger(RecommendServiceImpl.class);
	
	private Set<String> updateTaskQueue =Collections.synchronizedSet(new HashSet<String>());

	
	/**
	 * @author Bright_Zheng
	 * */
	@Override
	public Set<String> recordU2UChange(String uid, String cpid, String property, int selectType) {
		logger.debug("用户:"+uid+" 选择了CP："+cpid+"  的记录线程启动");
		long startTime = System.currentTimeMillis();
		
		Set<String> pendingPushUids = new HashSet<String>();
		
	
		/* Step 1.得到与用户U相同选择一CP的用户列表：
		 	*通过C2U表得到选过相同C的用户列表{Ui}，并将U加到C中
		 * */
		Set<String> usersSelectedSameCp= c2uDao.getUsersSelectedSameCp(cpid,property);		
		if(selectType == RecommendService.SELECT_CP){
			c2uDao.saveCpOneUser(cpid, uid, property);
		}else{
			c2uDao.deleteUserInCp(cpid, uid, property);
		}
		
		/*Step 2.更新U2U_Update_Status
		  *遍历{Ui}，在U2U_Update_Status表中U的关系用户列表里对Ui的∆u_score值加上刚选中CP的score值，没有则新增
		  *同时也在Ui的关系用户列表里找到U，将其∆u_score值加上刚选中CP的score值，没有则新增。
		*/
		Double dValue = concernPointDao.getConcernPointById(new BigInteger(cpid)).getWeight().doubleValue();
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
		
		
		/*3.如果是正向选择，就记录状态改变
		 	*通过U2U_Relation表得到所有和U有关系的用户{Uj},在U2U_Update_Status表中为每个Uj记录上U（增加的∆u_score为0）
		 	*返回所有需要触发更新任务的用户
	  	**/
		if(property.equals(RecommendService.POSITIVE_SELECT)){
			Set<Tuple> relatedUsers=u2uRelationDao.getRelatedUsersByRank(uid, 0, -1);//0表示第一个，-1为倒数第一个，即为获取所有关系用户
			Set<String> relatedUids = new HashSet<String>();
			for(Tuple user:relatedUsers){
				if(user.getScore()<=0){
					break;
				}
				relatedUids.add(user.getElement());
				
			}

			pendingPushUids.addAll(relatedUids);
			
			relatedUids.removeAll(usersSelectedSameCp);//产生了∆u_score的User不需要重复记录
			for(String relatedUid:relatedUids){
				u2uUpdateStatusDao.updateDeltaRelationValue(relatedUid, uid, UPDATE_MARK);
			}
		}
		pendingPushUids.addAll(usersSelectedSameCp);
		
		long endTime = System.currentTimeMillis();
		logger.debug("用户:"+uid+" 选择了CP："+cpid+"  的记录任务完成"+"\t 选中相同CP的用户数: "+ 
						usersSelectedSameCp.size() +"\t 产生更新用户数："+ pendingPushUids.size()+"\n 执行时间: "+
						(endTime-startTime)+"毫秒");
		return pendingPushUids;
	}
	
	/**
	 * @author Bright Zheng
	 *  
	 * */
	@Override
	public Boolean updateU2C(String uid) {
		try {
			if(!ifUpdateExecutable(uid)){
				return false;
			}
			logger.debug("用户:"+uid+" 的更新任务启动");
			long startTime = System.currentTimeMillis();
			/*将任务加入任务队列
			 * */
			updateTaskQueue.add(uid);
			
			/*更新开始
			 * step 1：在U2U_Update_Status中获取U所需要更新的状态发生过变化的用户集合{Uj}。
			 * */
			Map<String,String> userUpdateStatusMap= u2uUpdateStatusDao.getUserUpdateStatus(uid);
			logger.debug("上次更新后有"+userUpdateStatusMap.size()+"个相关用户有了操作");
			
			
			/*step 2: 遍历{Uj}
			 * */
			for(Entry<String,String> changedUserEntry:userUpdateStatusMap.entrySet()){
				String changedUid = changedUserEntry.getKey();
				double uDeltaValue = Double.valueOf(changedUserEntry.getValue());
				
				/*得到Uj在U的update_time后更新的标签列表{CPi}
				 * */
				Timestamp lastUpdateTime = Timestamp.valueOf(userLastUpdateTimeDao.getUserLastUpdateTime(uid));
				List<CpChoiceDetailDO> newCps= cpChoiceDetailDao.getOperatedCpAfterTime(Long.valueOf(changedUid), lastUpdateTime);
				
				if(Math.abs(uDeltaValue - NO_CHANGE) < 1e-6){
					/*如∆u_score为0，更新U2C*/
					updateU2CAfterLastUpdated(newCps, uid, changedUid);
				}else{
					/*如∆u_score不为0  
					 * 更新U2U_Relation， 为U对应的Uj的关系值加上对应在U2U_Update_Status中的∆u_score
					 * */
					u2uRelationDao.updateUserRelationValue(uid, changedUid, uDeltaValue);
					
					/* 更新U2C
					 * */
					updateU2CAfterLastUpdated(newCps, uid, changedUid);
					updateU2CBeforeLastUpdated(uid, Long.valueOf(changedUid), lastUpdateTime, uDeltaValue);
				}
			}
			
			/*step 3: 将U在U2U_Update_Status中的记录删除，将U的update_time更新为当前时间。
			 * */
			u2uUpdateStatusDao.deleteU2uUpdateStatus(uid);
			userLastUpdateTimeDao.setUserLastUpdateTime(uid, new Timestamp(System.currentTimeMillis()).toString());

			long endTime = System.currentTimeMillis();
			logger.debug("用户:"+uid+" 更新完毕\n 执行时间: "+(endTime-startTime)+"毫秒");
			return true;
		} catch (Exception e) {
			logger.error("用户:"+uid+"更新任务出错："+e.getMessage(),e);
			return false;
		}finally{
			updateTaskQueue.remove(uid);
		}
	}

	@Override
	public Boolean ifUpdateExecutable(String uid) {
		/*准备工作: 检查任务队列
		 * 如果用户的上次更新的任务还在排队，则丢弃此次任务
		 * */
		if(updateTaskQueue.contains(uid)){
			logger.debug("用户:"+uid+" 的上一次更新任务还没结束，本次任务丢弃");
			return false;
		}

		/*准备工作: 检查上次更新时间
		 * 如果距离用户上次更新的时间过短，则丢弃本次任务
		 * */
		long startTime = System.currentTimeMillis();
		long lastUpadteTimeLong = Timestamp.valueOf(userLastUpdateTimeDao.getUserLastUpdateTime(uid)).getTime();
		if((startTime-lastUpadteTimeLong) < MIN_INTERVAL){
			logger.debug("离用户："+uid+" 的上一次更新间隔过短，任务放弃");
			return false;
		}
		return true;
	}
	
	/**
	 * 用户每次上线的初始化任务，包括
	 * 将last updated time存入redis, 触发一次更新任务
	 * */
	@Override
	public void initRecommendParm(User u) {
		logger.debug("用户: "+ u.getName()+" 初始化推荐参数任务开始");
		Timestamp lastUpdateTime = u.getLast_update_time();
		if(lastUpdateTime==null){
			lastUpdateTime = new Timestamp(System.currentTimeMillis());
		}
		userLastUpdateTimeDao.setUserLastUpdateTime(u.getUserId().toString(), lastUpdateTime.toString());
		String uid = u.getUserId().toString();
		Boolean ifInited = u2cDao.ifUserCpInited(uid);
		if(!ifInited){
			logger.debug("用户: "+ u.getName()+" U2C列表不存在,初始化列表:");
			
			Map<String,Double> userCps= initialCpDao.getInitialCps();
			u2cDao.updateUserBatchCpValue(uid, userCps);
		}
		
		logger.debug("用户: "+ u.getName()+" 推荐参数初始化成功！");
	}
	
	@Override
	public void init(){
		if(!initialCpDao.ifexist()){
			logger.info("初始化 Redis InitialCP...");
			List<ConcernPointDO> initCps = concernPointDao.listConcernPointsByCreator();
			Map<String,Double> initCpsMap = new HashMap<String,Double>();

			Random randomData = new Random();
			for(ConcernPointDO cp:initCps){
				String cpId = cp.getId().toString();
				/*目前为每个赋值一个0-0.1之间的随机推荐值
				 * */
				double randomDouble = randomData.nextDouble();
				if(Math.abs(cp.getWeight().doubleValue() - 1.0) < 1e-6){
					initCpsMap.put(cpId, (randomDouble == 0?randomData.nextDouble():randomDouble) * INIT_CP_SCORE);
				}else{
					initCpsMap.put(cpId, ((randomDouble == 0?randomData.nextDouble():randomDouble) + 1.0) * INIT_CP_SCORE);
				}
			}
			initialCpDao.setCps(initCpsMap);
			logger.info("初始化 Redis InitialCP 完成！");
		}else{
			logger.info("===Redis InitialCP存在===");
		}
	}

	@Override
	public void replenish(String uid){
		if(u2cDao.ifNeedReplenish(uid)){
			logger.debug("用户: "+ uid+"推荐CP数量过少，新添。。。");
			
			Map<String,Double> replenishCps= initialCpDao.getRandomCps(REPLENISH_NUM);
			u2cDao.updateUserBatchCpValue(uid,replenishCps);
			
			logger.debug("用户: "+ uid+" U2C补充成功");
		}else{
			logger.debug("用户: "+ uid+" U2C数据充足");
		}
	}
	
	/**
	 * 将用户的lastUpdateTime从Redis同步到数据库中
	 * */
	@Override
	public void syncLastUpdateTime(User u) {
		if(u==null){
			logger.error("用户为空，同步更新时间失败");
			return;
		}
		logger.debug("用户: "+ u.getName()+" 下线，将更新时间同步到数据库");
		String userLastUpdateTimeStr = userLastUpdateTimeDao.getUserLastUpdateTime(u.getUserId().toString());
		Timestamp lastUpdateTime = null;
		if(userLastUpdateTimeStr == null){
			lastUpdateTime = new Timestamp(System.currentTimeMillis());
		}else{
			lastUpdateTime = Timestamp.valueOf(userLastUpdateTimeDao.getUserLastUpdateTime(u.getUserId().toString()));
		}
		u.setLast_update_time(lastUpdateTime);
		userDao.updateUser(u);
	}
	
	
	@Override
	public void signCpsPresented(String uid, List<String> cpIds) {
		if(cpIds.size()>0){
			u2cDao.setUserCpsPresented(uid, cpIds);
		}		
	}
	
	@Override
	public void setSelfAddCp(String cpid) {
		Map<String,Double> selfAddCp = new HashMap<String,Double>();
		selfAddCp.put(cpid, SELF_ADD_CP_SCORE);
		initialCpDao.setCps(selfAddCp);
		
		List<User> all_users = userDao.findAllUsers();
		for(User u:all_users){
			u2cDao.updateUserCpValue(u.getUserId().toString(), cpid, SELF_ADD_CP_SCORE);
		}		
	}
	
	/**更新Uj在U的update_time后更新的标签列表{CPi}
	 *	对每个CPi，在U的U2C表中对CPi的推荐分score 加上（ CPi自身的score * U-Uj的关系值u_score），新增为正，取消为负。
	 * */
	private void updateU2CAfterLastUpdated(List<CpChoiceDetailDO> newCps, String uid, String changedUid){
		//logger.info("新选CP更新：关联用户 "+changedUid+" Cp counts:"+newCps.size());
		for(CpChoiceDetailDO selectedCp:newCps){
			BigInteger selectedCpid = selectedCp.getCp_id();
			String is_selected = selectedCp.getIs_selected();
			String property = selectedCp.getProperty();
			Double cpWeight = concernPointDao.getConcernPointById(selectedCpid).getWeight().doubleValue();
			Double relateScore = u2uRelationDao.getRelatedUserScore(uid, changedUid);
			//logger.info("selectedCp: "+selectedCpid+" ; " + is_selected +" ; "+ property +" ; "+ cpWeight +" ; "+ relateScore);
			
			if(property.equals(RecommendService.POSITIVE_SELECT)){
				if(is_selected.equals(CpChoiceDetailDao.SELECTED)){
					if(cpChoiceDao.getCpChoice(Long.valueOf(changedUid), selectedCpid)==null){
						u2cDao.updateUserCpValue(uid, selectedCpid.toString(), cpWeight*relateScore);
					}
				}else{
					 //为取消标签时，如果在更新之前并未选中过，说明是选择又取消，应该什么都不做，只有选中过，取消才有意义
					if(cpChoiceDao.getCpChoice(Long.valueOf(changedUid), selectedCpid)!=null){
						u2cDao.updateUserCpValue(uid, selectedCpid.toString(), -cpWeight*relateScore);
					}
				}
			}else{
				if(is_selected.equals(CpChoiceDetailDao.SELECTED)){
					if(cpChoiceDao.getCpChoice(Long.valueOf(changedUid), selectedCpid)==null){
						u2cDao.updateUserCpValue(uid, selectedCpid.toString(), -cpWeight*relateScore);
					}
				}else{
					if(cpChoiceDao.getCpChoice(Long.valueOf(changedUid), selectedCpid)!=null){
						u2cDao.updateUserCpValue(uid, selectedCpid.toString(), cpWeight*relateScore);
					}
				}
			}
		}
	}
	
	/**更新Uj在U的update_time前已选的标签列表{CPj}
	 *	对每个CPj，在U的U2C表中对CPj的推荐分score 加上（ CPi自身的score * U-Uj的∆u_score）
	 * */
	private void updateU2CBeforeLastUpdated(String uid,Long changedUid,Timestamp lastUpdateTime,double uDeltaValue){
		//logger.info("已选CP更新：关联用户 "+changedUid);
		List<CpChoiceDO> oldCps = cpChoiceDao.getSelectedCpsBeforeTime(changedUid, lastUpdateTime);
		for(CpChoiceDO oldCp:oldCps){
			BigInteger oldCpId = oldCp.getCp_id();
			String property = oldCp.getProperty();
			Double cpWeight = concernPointDao.getConcernPointById(oldCpId).getWeight().doubleValue();
			if(property.equals(RecommendService.POSITIVE_SELECT)){
				u2cDao.updateUserCpValue(uid, oldCpId.toString(), cpWeight*uDeltaValue);
			}else{
				u2cDao.updateUserCpValue(uid, oldCpId.toString(), -cpWeight*uDeltaValue);
			}
		}
	}



}
