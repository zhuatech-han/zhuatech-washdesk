// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 顾客及明确企业月结授信额度。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer")
public class Customer extends RecordBase {
  @Column(nullable = false, length = 120)
  public String name = "";

  @Column(nullable = false, length = 120)
  public String contact = "";

  @Column(nullable = true)
  public Long accountId;

  @Column(nullable = false, length = 20)
  public String billing = "RETAIL";

  @Column(nullable = false, length = 3)
  public String currency = "CNY";

  @Column(nullable = false)
  public long creditLimit = 0;

  @Column(nullable = false)
  public boolean enabled = true;
}
