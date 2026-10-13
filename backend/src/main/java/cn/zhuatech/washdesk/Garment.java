// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 逐件身份、外观、加工和挂架轨迹。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "garment")
public class Garment extends RecordBase {
  @Column(nullable = false)
  public Long orderId;

  @Column(nullable = false, length = 60)
  public String barcode = "";

  @Column(nullable = false, length = 200)
  public String description = "";

  @Column(nullable = false, length = 1000)
  public String conditionNote = "";

  @Column(nullable = false, length = 120)
  public String serviceName = "";

  @Column(nullable = false, length = 120)
  public String serviceNameEn = "";

  @Column(nullable = false)
  public long price = 0;

  @Column(nullable = false, length = 20)
  public String state = "RECEIVED";

  @Column(nullable = true)
  public Long rackId;

  @Column(nullable = false)
  public int rewashCount = 0;

  @Column(nullable = true)
  public Instant deliveredAt;
}
