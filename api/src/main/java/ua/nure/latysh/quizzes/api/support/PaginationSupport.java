package ua.nure.latysh.quizzes.api.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaginationSupport {
    public static final String PAGE_NUMBER_HEADER = "X-Page-Number";
    public static final String PAGE_SIZE_HEADER = "X-Page-Size";
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    public static final String TOTAL_PAGES_HEADER = "X-Total-Pages";
    private static final int DEFAULT_PAGE_SIZE = 20;

    public Pageable pageable(Integer page, Integer size) {
        if (page == null && size == null) {
            return Pageable.unpaged();
        }
        return PageRequest.of(page == null ? 0 : page, size == null ? DEFAULT_PAGE_SIZE : size);
    }

    public <T> ResponseEntity<List<T>> response(Page<T> result) {
        return responseBuilder(result).body(result.getContent());
    }

    public <T> ResponseEntity<List<T>> response(Page<T> result, CacheControl cacheControl) {
        return responseBuilder(result).cacheControl(cacheControl).body(result.getContent());
    }

    private static ResponseEntity.BodyBuilder responseBuilder(Page<?> result) {
        return ResponseEntity.ok()
                .header(PAGE_NUMBER_HEADER, Integer.toString(result.getNumber()))
                .header(PAGE_SIZE_HEADER, Integer.toString(result.getSize()))
                .header(TOTAL_COUNT_HEADER, Long.toString(result.getTotalElements()))
                .header(TOTAL_PAGES_HEADER, Integer.toString(result.getTotalPages()));
    }
}
