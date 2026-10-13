// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 门店挂架容量，不允许容量低于实时占用。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "rack")
public class Rack extends RecordBase {
  @Column(nullable = false, length = 60)
  public String code = "";

  @Column(nullable = false, length = 120)
  public String name = "";

  @Column(nullable = false)
  public int capacity = 1;

  @Column(nullable = false)
  public boolean enabled = true;
}
