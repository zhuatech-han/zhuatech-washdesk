// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 不可变收款退款台账，引用凭据全局唯一。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry extends RecordBase {
  @Column(nullable = false, length = 20)
  public String targetType = "";

  @Column(nullable = false)
  public Long targetId;

  @Column(nullable = true)
  public Long originalId;

  @Column(nullable = false, length = 20)
  public String kind = "";

  @Column(nullable = false)
  public long amount = 0;

  @Column(nullable = false, length = 3)
  public String currency = "";

  @Column(nullable = false, length = 120)
  public String reference = "";

  @Column(nullable = false, length = 30)
  public String method = "";

  @Column(nullable = false, length = 60)
  public String actor = "";

  @Column(nullable = false)
  public Instant createdAt;
}
