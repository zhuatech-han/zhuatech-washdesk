// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.io.*;
import java.time.Clock;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 外观附件逐次鉴权，数据库持久化并限制解码尺寸。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class PhotoService {
  final Store db;
  final WashDeskService laundry;
  final AdminService admin;
  final Clock clock;

  public PhotoService(Store db, WashDeskService laundry, AdminService admin, Clock clock) {
    this.db = db;
    this.laundry = laundry;
    this.admin = admin;
    this.clock = clock;
  }

  /** 仅接收合法JPEG／PNG，拒绝伪扩展名和像素超限，不信任上传者MIME。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String validate(byte[] data) {
    if (data.length < 12 || data.length > 2 * 1024 * 1024) throw new Problem(413, "FILE_TOO_LARGE");
    boolean png =
        data[0] == (byte) 137
            && data[1] == 80
            && data[2] == 78
            && data[3] == 71
            && data[4] == 13
            && data[5] == 10
            && data[6] == 26
            && data[7] == 10;
    boolean jpg = data[0] == (byte) 255 && data[1] == (byte) 216 && data[2] == (byte) 255;
    Rules.check(png || jpg, "PHOTO_INVALID");
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(stream);
      Rules.check(readers.hasNext(), "PHOTO_INVALID");
      var reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        long width = reader.getWidth(0), height = reader.getHeight(0);
        Rules.check(
            width > 0
                && height > 0
                && width <= 4096
                && height <= 4096
                && width * height <= 12000000,
            "PHOTO_INVALID");
        Rules.check(reader.read(0) != null, "PHOTO_INVALID");
      } finally {
        reader.dispose();
      }
    } catch (IOException | IndexOutOfBoundsException e) {
      throw new Problem(400, "PHOTO_INVALID");
    }
    return png ? "image/png" : "image/jpeg";
  }

  /** 加工前按衣物版本追加私有照片，同一事务更新订单轨迹。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object upload(Long id, long version, MultipartFile file) {
    admin.lock();
    var g = db.get(Garment.class, id);
    laundry.scope(g.departmentId, "orders");
    Rules.check(g.version == version, "VERSION_CONFLICT");
    var o = db.get(WashOrder.class, g.orderId);
    Rules.check(o.state.equals("OPEN") && g.state.equals("RECEIVED"), "PHOTO_LOCKED");
    Rules.check(
        db.query(GarmentPhoto.class, "from GarmentPhoto where garmentId=?1", id).size()
            < admin.setting("photoLimit"),
        "PHOTO_LIMIT");
    byte[] data;
    try {
      data = file.getBytes();
    } catch (IOException e) {
      throw new Problem(400, "PHOTO_INVALID");
    }
    String media = validate(data);
    var p = new GarmentPhoto();
    p.departmentId = g.departmentId;
    p.garmentId = g.id;
    p.data = data;
    p.mediaType = media;
    p.createdAt = clock.instant();
    db.save(p);
    g.version++;
    o.version++;
    laundry.event("ORDER", o.id, o.departmentId, "PHOTO_ADD", g.barcode);
    return java.util.Map.of("id", p.id, "garmentId", id);
  }

  /** 对所属订单重新鉴权后返回字节，不信任文件名或路径。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public GarmentPhoto read(Long id) {
    var p = db.get(GarmentPhoto.class, id);
    var g = db.get(Garment.class, p.garmentId);
    laundry.read(db.get(WashOrder.class, g.orderId));
    return p;
  }
}
