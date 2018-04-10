package so.xunta.beans;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 2018.03.30  保存用户的话题的未读消息数
 * @author 叶夷
 *
 */
@Entity
@Table(name="tbl_topic_unreadnum")
public class TopicHasUnreadMsgNum extends IdEntity{
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private int id;
	@Column(unique=true,nullable=false)
	private Long topicid;
	private int unreadNum;
	private Long userid;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public Long getUserid() {
		return userid;
	}
	public void setUserid(Long userid) {
		this.userid = userid;
	}
	public Long getTopicid() {
		return topicid;
	}
	public void setTopicid(Long topicid) {
		this.topicid = topicid;
	}
	public int getUnreadNum() {
		return unreadNum;
	}
	public void setUnreadNum(int unreadNum) {
		this.unreadNum = unreadNum;
	}
	public TopicHasUnreadMsgNum() {
	}
	public TopicHasUnreadMsgNum(Long userid, Long topicid, int unreadNum) {
		this.userid = userid;
		this.topicid = topicid;
		this.unreadNum = unreadNum;
	}
}
