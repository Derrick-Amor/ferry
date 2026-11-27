package seb.controller;

import seb.auth.UserContext;
import seb.common.Result;
import seb.dto.CartAddDTO;
import seb.dto.CartUpdateDTO;
import seb.service.CartService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Resource
    private CartService cartService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(cartService.list(UserContext.getUserId()));
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody CartAddDTO dto) {
        cartService.add(UserContext.getUserId(), dto.getCourseId());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CartUpdateDTO dto) {
        cartService.update(UserContext.getUserId(), id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }
}
