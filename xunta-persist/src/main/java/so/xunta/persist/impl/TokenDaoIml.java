package so.xunta.persist.impl;

import java.util.List;

import javax.transaction.Transactional;

import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import so.xunta.beans.Token;
import so.xunta.persist.TokenDao;
/**
 * 2017.11.29 对微信公众号token的操作
 * @author 叶夷
 *
 */
@Transactional
@Repository
public class TokenDaoIml implements TokenDao {
	Logger logger =Logger.getLogger(TokenDaoIml.class);
	@Autowired
	SessionFactory sessionFactory;

	@SuppressWarnings("unchecked")
	@Override
	public List<Token> getTokenForAppid(String appid) {
		//System.out.println("开始测试Dao查找token1"+appid);
		Session session = sessionFactory.getCurrentSession();
		//System.out.println("开始测试Dao查找token2");
		/*String hql = "from tbl_token where appid = :appId";
		Query query = session.createQuery(hql).setParameter("appId",appid);
		System.out.println("测试Dao查找token:"+query.uniqueResult());*/
		String sql = "select * from tbl_token where appid = :appId";
		Query query = session.createSQLQuery(sql).addEntity(Token.class).setParameter("appId",appid);
		//System.out.println("测试Dao查找token:"+query.list());
		return query.list();
	}

	@Override
	public Token saveToken(Token token) {
		Session session = sessionFactory.getCurrentSession();
		session.save(token);
		//System.out.println("测试Dao保存token:"+token.getAppid());
		return token;
	}

	@Override
	public Token updateToken(Token token) {
		Session session = sessionFactory.getCurrentSession();
		session.update(token);
		System.out.println("测试Dao更新token:"+token.getAppid()+" "+token.getAccessToken());
		return token;
	}
}
