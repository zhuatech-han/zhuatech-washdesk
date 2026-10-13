// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 按类别和币种维护的收费服务。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "wash_service")
public class WashService extends RecordBase {
  @Column(nullable = false, length = 120)
  public String name = "";

  @Column(nullable = false, length = 120)
  public String nameEn = "";

  @Column(nullable = false, length = 60)
  public String category = "";

  @Column(nullable = false, length = 3)
  public String currency = "CNY";

  @Column(nullable = false)
  public long price = 0;

  @Column(nullable = false)
  public boolean enabled = true;
}
