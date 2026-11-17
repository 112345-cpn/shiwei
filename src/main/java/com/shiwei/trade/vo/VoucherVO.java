package com.shiwei.trade.vo;

import com.shiwei.trade.entity.Voucher;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherVO {

    private Long id;
    private Long merchantId;
    private Long shopId;
    private String shopName;

    private String title;
    private String subTitle;
    private String rules;

    /** 0 代金券 1 套餐 2 单品 */
    private Integer type;
    private String typeDesc;

    private Integer originalPrice;
    private Integer salePrice;

    /** 展示用的折扣文案，如「6.7折」；原价为 0 时给空串 */
    private String discountText;

    private Integer stock;
    private Integer sold;

    private LocalDateTime saleBegin;
    private LocalDateTime saleEnd;
    private LocalDateTime validBegin;
    private LocalDateTime validEnd;

    private Integer status;
    private String statusDesc;

    /** 当前是否还能买：上架 + 在售 + 有库存，前端不用自己算 */
    private Boolean onSale;

    public static VoucherVO of(Voucher v) {
        VoucherVO vo = new VoucherVO();
        vo.setId(v.getId());
        vo.setMerchantId(v.getMerchantId());
        vo.setShopId(v.getShopId());
        vo.setTitle(v.getTitle());
        vo.setSubTitle(v.getSubTitle());
        vo.setRules(v.getRules());
        vo.setType(v.getType());
        vo.setTypeDesc(switch (v.getType() == null ? 0 : v.getType()) {
            case 1 -> "套餐";
            case 2 -> "单品";
            default -> "代金券";
        });
        vo.setOriginalPrice(v.getOriginalPrice());
        vo.setSalePrice(v.getSalePrice());
        vo.setDiscountText(discountText(v.getOriginalPrice(), v.getSalePrice()));
        vo.setStock(v.getStock());
        vo.setSold(v.getSold());
        vo.setSaleBegin(v.getSaleBegin());
        vo.setSaleEnd(v.getSaleEnd());
        vo.setValidBegin(v.getValidBegin());
        vo.setValidEnd(v.getValidEnd());
        vo.setStatus(v.getStatus());
        vo.setStatusDesc(switch (v.getStatus() == null ? 0 : v.getStatus()) {
            case 1 -> "已上架";
            case 2 -> "已下架";
            default -> "草稿";
        });
        return vo;
    }

    /**
     * 折扣按「售价 / 原价」算，保留一位小数向下取。
     * 用整数运算而不是 double，避免 0.1 之类的表示误差把 6.7 算成 6.6。
     */
    private static String discountText(Integer original, Integer sale) {
        if (original == null || sale == null || original <= 0 || sale <= 0) {
            return "";
        }
        if (sale >= original) {
            return "";
        }
        int tenth = sale * 100 / original;
        return (tenth / 10) + "." + (tenth % 10) + "折";
    }
}
