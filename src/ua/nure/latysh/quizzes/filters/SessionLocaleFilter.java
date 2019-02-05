package ua.nure.latysh.quizzes.filters;

import org.apache.log4j.Logger;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

@WebFilter(filterName = "SessionLocaleFilter", urlPatterns = {"/*"})
public class SessionLocaleFilter implements Filter {
    private final static Logger logger = Logger.getLogger(SessionLocaleFilter.class);
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        req.setCharacterEncoding("UTF-8");
        String langSelect = req.getParameter("lang");

        if (langSelect != null && langSelect.equalsIgnoreCase("ru")) {
            Locale locale = new Locale("ru");
            ResourceBundle resourceBundle = ResourceBundle.getBundle("messages", locale);
            Locale selectedLocale = resourceBundle.getLocale();

            req.getSession().setAttribute("lang", selectedLocale);
            chain.doFilter(request, response);
            logger.info("Language was set to " + selectedLocale.getLanguage());
        } else if (langSelect != null && langSelect.equalsIgnoreCase("en")) {
            Locale locale = new Locale("en_GB");
            ResourceBundle resourceBundle = ResourceBundle.getBundle("messages", locale);
            Locale selectedLocale = resourceBundle.getLocale();

            req.getSession().setAttribute("lang", selectedLocale);
            chain.doFilter(request, response);
            logger.info("Language was set to " + selectedLocale.getLanguage());
        } else if(req.getSession().getAttribute("lang") != null){

            chain.doFilter(request, response);
        }
        else {
            Locale locale = req.getLocale();
            ResourceBundle resourceBundle = ResourceBundle.getBundle("messages", locale);
            Locale selectedLocale = resourceBundle.getLocale();

            req.getSession().setAttribute("lang", selectedLocale);

            chain.doFilter(request, response);
            logger.info("Language was set to " + locale.getLanguage());

        }
    }

    public void destroy() {
    }

    public void init(FilterConfig arg0) throws ServletException {
    }
}
