// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 冻结客户、币种和收费快照的收衣订单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "wash_order")
public class WashOrder extends RecordBase {
  @Column(nullable = false, length = 60)
  public String number = "";

  @Column(nullable = false)
  public Long customerId;

  @Column(nullable = false, length = 120)
  public String customerName = "";

  @Column(nullable = false, length = 3)
  public String currency = "";

  @Column(nullable = false, length = 20)
  public String billing = "";

  @Column(nullable = false)
  public LocalDate dueDate;

  @Column(nullable = false, length = 20)
  public String state = "OPEN";

  @Column(nullable = false)
  public long total = 0;

  @Column(nullable = false, length = 2000)
  public String note = "";

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = true)
  public Instant completedAt;

  @Column(nullable = true)
  public Long invoiceId;
}
