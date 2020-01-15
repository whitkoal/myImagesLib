package cn.bruce.security.core.validate.code;

import cn.bruce.security.core.properties.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * 图片验证码过滤器。继承了OncePerRequestFilter过滤器，保证每次请求只会经过一次过滤器。
 */
public class ValidateCodeFilter extends OncePerRequestFilter implements InitializingBean {

    private AuthenticationFailureHandler authenticationFailureHandler;

    private SecurityProperties securityProperties;

    private AntPathMatcher pathMatcher = new AntPathMatcher();

    private Set<String> urls = new HashSet<>();

    /**
     * 将所有需要使用验证码的url请求的地址都放到一个Set集合中。
     * @throws ServletException
     */
    @Override
    public void afterPropertiesSet() throws ServletException {
        super.afterPropertiesSet();
        String[] configUrls = securityProperties.getCode().getImage().getUrl().split(",");
        for (String url : configUrls){
            urls.add(url);
        }
        urls.add("/authentication/form");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        boolean action = false;
        for(String url : urls){
            if(pathMatcher.match(url, request.getRequestURI())){
                action = true;
            }
        }

        // 验证请求是否合法，请求路径正确，而且必须是POST请求
//        StringUtils.pathEquals("/authentication/form", request.getRequestURI())
//                && request.getMethod().equals("POST")
        if (action) {
            try {
                // 用于校验图片验证码的方法。
                validated(request);
            } catch (validateCodeException e) {
                authenticationFailureHandler.onAuthenticationFailure(request, response, e);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 用于校验图片验证码的方法。
     *
     * @param request
     */
    private void validated(HttpServletRequest request) throws ServletRequestBindingException {
        System.out.println("进入图片验证");
        ImageCode codeInSession = (ImageCode) request.getSession().getAttribute(validateCodeController.SESSION_KEY);
        String codeInRequest = ServletRequestUtils.getStringParameter(request, "imageCode");

        if (codeInRequest == null || codeInRequest.isEmpty() || codeInRequest.isBlank()) {
            throw new validateCodeException("验证码的值不能为空！");
        }

        if (codeInSession == null) {
            throw new validateCodeException("验证码不存在！");
        }

        if (codeInSession.isExpired()) {
            // Session过期，清除Session。
            request.getSession().removeAttribute(validateCodeController.SESSION_KEY);
            System.out.println("Session是否移除：--" + request.getSession().getAttribute(validateCodeController.SESSION_KEY) + "————");
            throw new validateCodeException("验证码已过期！");
        }

        if (!codeInRequest.equals(codeInSession.getCode())) {
            throw new validateCodeException("验证码不匹配！");
        }
        // 如果以上判断都通过，则说明验证码验证成功，将Session清除。
        request.getSession().removeAttribute(validateCodeController.SESSION_KEY);
    }

    public AuthenticationFailureHandler getAuthenticationFailureHandler() {
        return authenticationFailureHandler;
    }

    public void setAuthenticationFailureHandler(AuthenticationFailureHandler authenticationFailureHandler) {
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    public SecurityProperties getSecurityProperties() {
        return securityProperties;
    }

    public void setSecurityProperties(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }
}