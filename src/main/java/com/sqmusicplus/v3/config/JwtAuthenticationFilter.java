package com.sqmusicplus.v3.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired private com.sqmusicplus.v3.members.MemberService members;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LoginCheckInterceptor loginCheckInterceptor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 登录接口永远不校验请求头 token（无论是否携带、是否过期），直接透传
        if ("/api/config/login".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getTokenFromRequest(request);
        
//        if (StringUtils.hasText(token)) {
//            log.debug("从请求中获取到 token: {}", token.substring(0, Math.min(20, token.length())) + "...");
//        }

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Integer userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            Integer version=jwtUtil.getClaimsFromToken(token).get("mv",Integer.class);
            if(userId==null||!members.session(userId,username,version)){response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":401,\"msg\":\"登录已失效，请重新登录\"}");return;}

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, new ArrayList<>()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
//            log.info("用户 {} 认证成功，ID: {}", username, userId);
        } else if (StringUtils.hasText(token)) {
            log.warn("Token 验证失败");
        }

        // 检查是否需要登录认证
        if (!loginCheckInterceptor.checkLogin(request)) {
            // 未登录或 Token 无效，返回 401
            log.warn("⚠请求被拒绝，需要登录: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
            return;
        }

        String path=request.getRequestURI(),method=request.getMethod();
        Integer member=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()!=null&&org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Integer id?id:null;
        boolean publicMedia=path.startsWith("/api/library/stream/")||path.startsWith("/api/library/cover/")||path.startsWith("/api/proxy/tidal/");
        boolean publicInfo=java.util.Set.of("/api/config/isLogin","/api/config/version","/api/version","/api/config/getOption","/api/config/getPlugBrTypeList").contains(path);
        if(!method.equals("OPTIONS")&&(path.startsWith("/api/")||path.equals("/mcp"))&&!publicMedia&&!publicInfo&&member==null){response.setStatus(401);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\"}");return;}
        if(member!=null&&member!=com.sqmusicplus.v3.members.MemberScope.ADMIN&&!com.sqmusicplus.v3.members.MemberAccess.allowed(method,path)&&(path.startsWith("/api/")||path.equals("/mcp"))){response.setStatus(403);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":403,\"msg\":\"此操作仅管理员可用\"}");return;}
        if(member==null){filterChain.doFilter(request,response);return;}
        try(com.sqmusicplus.v3.members.MemberScope scope=com.sqmusicplus.v3.members.MemberScope.enter(member)){filterChain.doFilter(request,response);}
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // 优先从 Authorization header 读取 (标准格式: Bearer xxx)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // 兼容自定义 sqmusic header (不区分大小写)
        String sqmusicToken = request.getHeader("sqmusic");
        if (StringUtils.hasText(sqmusicToken)) {
            return sqmusicToken;
        }
        
        // 尝试其他可能的大小写形式
        String[] possibleHeaders = {"SqMusic", "SQMUSIC", "Sqmusic"};
        for (String headerName : possibleHeaders) {
            String token = request.getHeader(headerName);
            if (StringUtils.hasText(token)) {
                log.debug("从 {} header 获取 token", headerName);
                return token;
            }
        }
        
        return null;
    }
}
