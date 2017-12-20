package java100.app.servlet;

import static org.reflections.ReflectionUtils.getMethods;
import static org.reflections.ReflectionUtils.withAnnotation;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java100.app.annotation.RequestMapping;
import java100.app.annotation.RequestParam;
import java100.app.listener.ContextLoaderListener;

// 프론트 컨트롤러(Front Controller) 
// - 페이지 컨트롤러의 앞 쪽에서 요청을 먼저 받는다.
// - 페이지 컨트롤러가 사용하기 쉽게 요청 데이터를 가공한다.
// - 페이지 컨트롤러가 작업을 완료하면 그 작업 결과를 출력할 JSP를 실행한다.
// - 또는 redirect 한다.
// - "Facade" 설계 패턴과 같다.
// 
@WebServlet("*.do")
@SuppressWarnings("serial")
public class DispatcherServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        // 클라이언트가 요청한 URL에서 .do를 제외한 
        // 페이지 컨트롤로의 경로를 추출한다.
        String pageControllerPath = request.getServletPath().replace(".do", "");
        
        // 스프링 IoC 컨테이너에서 페이지 컨트롤러를 찾는다.
        Object pageController = 
          ContextLoaderListener.iocContainer.getBean(pageControllerPath);
        
        if (pageController == null) {
            throw new ServletException("페이지 컨트롤러를 찾을 수 없습니다.");
        }

        // 페이지 컨트롤러 객체에서 @RequestMapping이 붙은 메서드를 찾는다.
        @SuppressWarnings("unchecked")
        Set<Method> methods = getMethods(pageController.getClass(),
                withAnnotation(RequestMapping.class));
        
        if (methods.size() == 0) {
            throw new ServletException(
                    "페이지 컨트롤러의 요청 핸들러를 찾을 수 없습니다.");
        }
        
        // 메서드 목록에서 첫 번째 메서드를 꺼낸다.
        Method requestHandler = (Method)methods.toArray()[0];
        
        // 메서드의 파라미터를 분석하여 그 파라미터가 원하는 값을 준비한다.
        Object[] paramValues = getParamValuesFor(
                requestHandler, request, response);
        
        try {
            // 페이지 컨트롤러를 요청 핸들러를 호출한다.
            String viewName = (String)requestHandler.invoke(
                    pageController, paramValues);
            
            if (viewName.startsWith("redirect:")) {
                response.sendRedirect(viewName.substring(9));
                return;
            } else {
                // JSP를 인클루딩하기 전에 콘텐츠 타입을 설정해야 한다.
                // => 인클루드 되는 쪽에서는 여기에서 설정한 것을 그대로 따른다.
                response.setContentType("text/html;charset=UTF-8");
                RequestDispatcher rd = request.getRequestDispatcher(viewName);
                rd.include(request, response);
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private Object[] getParamValuesFor(
            Method method, 
            HttpServletRequest request, 
            HttpServletResponse response) {
        
        // 메서드에 파라미터 목록을 가져온다.
        Parameter[] params = method.getParameters();
        
        // 파라미터 개수 만큼 값을 담을 배열을 준비한다.
        Object[] values = new Object[params.length];
        
        for (int i = 0; i < params.length; i++) {
            if (params[i].getType() == HttpServletRequest.class) {
                values[i] = request;
            } else if (params[i].getType() == HttpServletResponse.class) {
                values[i] = response;
            } else {
                // 파라미터에 붙인 @RequestParam 정보를 가져온다.
                RequestParam anno = params[i].getAnnotation(RequestParam.class);
                
                // 애노테이션의 value 값으로 클라이언트가 보낸 값을 찾는다.
                String value = request.getParameter(anno.value());
                values[i] = toParamTypeValue(params[i], value);
            }
        }
        
        return values;
    }
    
    private Object toParamTypeValue(Parameter param, String value) {
        
        Class<?> type = param.getType();
        
        // 파라미터의 타입에 따라 String 값을 그 타입의 값으로 형변환한다.
        if (type == byte.class) {
            return Byte.parseByte(value);
        } else if (type == short.class) {
            return Short.parseShort(value);
        } else if (type == int.class) {
            return Integer.parseInt(value);
        } else if (type == long.class) {
            return Long.parseLong(value);
        } else if (type == float.class) {
            return Float.parseFloat(value);
        } else if (type == double.class) {
            return Double.parseDouble(value);
        } else if (type == boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == char.class) {
            return value.charAt(0);
        } else if (type == String.class){
            return value;
        } else {
            return null;
        }
    }
}










