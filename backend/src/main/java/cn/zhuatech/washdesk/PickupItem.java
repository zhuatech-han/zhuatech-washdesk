// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 取衣记录内的核对衣物身份。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pickup_item")
public class PickupItem extends RecordBase {
  @Column(nullable = false)
  public Long pickupId;

  @Column(nullable = false)
  public Long garmentId;

  @Column(nullable = false, length = 60)
  public String barcode = "";
}
