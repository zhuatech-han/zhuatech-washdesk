// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 冻结已取完企业订单成员的月结单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "monthly_invoice")
public class MonthlyInvoice extends RecordBase {
  @Column(nullable = false, length = 60)
  public String number = "";

  @Column(nullable = false)
  public Long customerId;

  @Column(nullable = false, length = 120)
  public String customerName = "";

  @Column(name = "billing_month", nullable = false, length = 7)
  public String month = "";

  @Column(nullable = false, length = 3)
  public String currency = "";

  @Column(nullable = false)
  public long total = 0;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false, length = 20)
  public String state = "OPEN";
}
