package quick.pager.shop.risk.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import quick.pager.shop.constants.Constants;
import quick.pager.shop.response.Response;

/**
 * 风控管理
 *
 * @author siguiyang
 */
@RestController(value = Constants.Module.RISK)
public class RiskController {


    /**
     * 用户下单限制
     */
    @PostMapping("/order")
    public Response order() {
        return null;
    }
}
