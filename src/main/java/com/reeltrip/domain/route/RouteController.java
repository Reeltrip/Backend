package com.reeltrip.domain.route;

import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.*;
import com.reeltrip.global.security.LoginUser;

@RestController
public class RouteController {
    private final RouteService service;
    private final UserRepository users;
    public RouteController(RouteService service,UserRepository users) { this.service=service; this.users=users; }
    @GetMapping("/routes")
    public ResponseEntity<RouteService.Response> get(@LoginUser Long userId,@RequestParam Long from,@RequestParam Long to,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime departAt) {
        if(!users.existsById(userId)) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        if(from<=0||to<=0||from.equals(to)) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(userId,from,to,departAt));
    }
}
