// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;

/** 请求UUID绑定操作者、载荷和结果身份，避免重复扣款扣时。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "command_stamp")
public class CommandStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 36)
  public String requestId;

  @Column(nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 64)
  public String fingerprint;

  @Column(nullable = false, length = 30)
  public String resultType;

  @Column(nullable = false)
  public Long resultId;
}
