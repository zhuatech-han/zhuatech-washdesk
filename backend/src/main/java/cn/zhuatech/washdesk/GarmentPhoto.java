// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import jakarta.persistence.*;
import java.time.*;

/** 权限内保存衣物外观照片，不公开静态URL。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "garment_photo")
public class GarmentPhoto extends RecordBase {
  @Column(nullable = false)
  public Long garmentId;

  @Column(nullable = false, length = 30)
  public String mediaType = "";

  @Column(nullable = false)
  public Instant createdAt;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Lob
  @Column(nullable = false, columnDefinition = "longblob")
  public byte[] data;
}
