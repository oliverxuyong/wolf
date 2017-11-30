package so.xunta.persist;

import java.util.Set;

import redis.clients.jedis.Tuple;

public interface GroupMatchedUserDao {
	
	public void updatePairMatchedUser(String userGroup,String pairUserName,Double score);
	public Set<Tuple> getPairMatchedUsers(String userGroup);

}
