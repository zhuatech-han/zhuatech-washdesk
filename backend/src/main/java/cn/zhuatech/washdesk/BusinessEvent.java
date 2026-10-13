// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 保存对象、动作、理由与操作者，不写联系人或密码。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "business_event")
public class BusinessEvent extends RecordBase {
  @Column(nullable = false, length = 30)
  public String objectType = "";

  @Column(nullable = false)
  public Long objectId;

  @Column(nullable = false, length = 40)
  public String action = "";

  @Column(nullable = false, length = 60)
  public String actor = "";

  @Column(nullable = false, length = 2000)
  public String note = "";

  @Column(nullable = false)
  public Instant createdAt;
}
