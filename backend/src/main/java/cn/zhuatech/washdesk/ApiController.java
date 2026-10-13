// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 岗位流程和管理接口，每次重新检查权限、门店和对象状态。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final WashDeskService laundry;
  final AdminService admin;
  final PhotoService photos;

  public ApiController(WashDeskService laundry, AdminService admin, PhotoService photos) {
    this.laundry = laundry;
    this.admin = admin;
    this.photos = photos;
  }

  /** 读取当前岗位基础目录。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  public Object catalog() {
    return laundry.catalog();
  }

  /** 新增门店业务目录。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/masters/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody Map<String, Object> b) {
    return laundry.master(type, null, b);
  }

  /** 按版本更新目录并保护历史归属。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/masters/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return laundry.master(type, id, b);
  }

  /** 范围内搜索筛选分页排序订单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders")
  public Object orders(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String state,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "newest") String sort) {
    return laundry.orders(q, state, page, sort);
  }

  /** 冻结逐件价格并创建收衣订单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders")
  public Object order(@RequestBody Map<String, Object> b) {
    return laundry.createOrder(b);
  }

  /** 读取范围内完整订单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/orders/{id}")
  public Object detail(@PathVariable Long id) {
    return laundry.detail(id);
  }

  /** 核对逐件编号并完成分批取衣。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/pickup")
  public Object pickup(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return laundry.pickup(id, b);
  }

  /** 加工前取消订单并保留资金历史。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/orders/{id}/cancel")
  public Object cancel(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return laundry.cancel(id, b);
  }

  /** 验证合法加工质检返洗挂架流转。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/garments/{id}/{action}")
  public Object process(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return laundry.garment(id, action, b);
  }

  /** 上传加工前外观照片。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/garments/{id}/photos")
  public Object upload(
      @PathVariable Long id, @RequestParam long version, @RequestParam MultipartFile file) {
    return photos.upload(id, version, file);
  }

  /** 鉴权返回照片字节，不产生公开文件地址。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/photos/{id}")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> photo(@PathVariable Long id) {
    var p = photos.read(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(p.mediaType))
        .header("Cache-Control", "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(p.data);
  }

  /** 验证应收或原款余额并登记核实收退款。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/payments/{type}/{id}")
  public Object payment(
      @PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return laundry.payment(type, id, b);
  }

  /** 读取岗位或本人月结单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/invoices")
  public Object invoices() {
    return laundry.invoices();
  }

  /** 冻结未开单企业订单形成月结单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/invoices")
  public Object invoice(@RequestBody Map<String, Object> b) {
    return laundry.invoice(b);
  }

  /** 读取月结成员和资金台账。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/invoices/{id}")
  public Object invoiceDetail(@PathVariable Long id) {
    return laundry.invoiceDetail(id);
  }

  /** 门店内按币种统计订单资金和物品状态。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  public Object report() {
    return laundry.report();
  }

  /** 读取业务操作轨迹。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return laundry.audit();
  }

  /** 导出安全CSV。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/export")
  public ResponseEntity<String> export() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=washdesk-orders.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(laundry.export());
  }

  /** 账号配置可选角色和门店。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/options")
  public Object options() {
    return admin.options();
  }

  /** 鉴权读取管理目录。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{kind}")
  public Object readAdmin(@PathVariable String kind) {
    return admin.read(kind);
  }

  /** 新增管理目录并保护最后管理员。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{kind}")
  public Object createAdmin(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  /** 按版本更新管理目录和实时权限。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{kind}/{id}")
  public Object updateAdmin(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  /** 门店范围认证与配置审计。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/security-audit")
  public Object securityAudit() {
    return laundry.securityAudit();
  }
}
