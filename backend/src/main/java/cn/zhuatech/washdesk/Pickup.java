// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可变的分批取衣交接记录。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pickup")
public class Pickup extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false, length = 120)
  public String receiver = "";

  @Column(nullable = false, length = 60)
  public String actor = "";

  @Column(nullable = false, length = 1000)
  public String note = "";

  @Column(nullable = false)
  public Instant createdAt;
}
