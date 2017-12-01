package so.xunta.beans;

import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 凭证实体类
 */
@Entity
@Table(name="tbl_token")
public class Token extends IdEntity{
	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Long id;
	// 接口访问凭证
	private String accessToken;
	//微信公众号的id
	private String appid;
	// 创建时间
	private Timestamp createTime;
	// 无效时间  = 创建时间+有效时间
	private Timestamp failureTime;

	public Token() {
	}
	public Token(Long id, String accessToken, String appid, Timestamp createTime, Timestamp failureTime) {
		super();
		this.id = id;
		this.accessToken = accessToken;
		this.appid = appid;
		this.createTime = createTime;
		this.failureTime = failureTime;
	}
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getAccessToken() {
		return accessToken;
	}
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	public String getAppid() {
		return appid;
	}
	public void setAppid(String appid) {
		this.appid = appid;
	}
	public Timestamp getCreateTime() {
		return createTime;
	}
	public void setCreateTime(Timestamp createTime) {
		this.createTime = createTime;
	}
	public Timestamp getFailureTime() {
		return failureTime;
	}
	public void setFailureTime(Timestamp failureTime) {
		this.failureTime = failureTime;
	}
}
