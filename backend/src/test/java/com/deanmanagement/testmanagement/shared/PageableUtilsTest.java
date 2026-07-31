package com.deanmanagement.testmanagement.shared;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageableUtilsTest {

    @Test
    void unpaged_appliesDefaults() {
        Pageable result = PageableUtils.normalize(Pageable.unpaged());

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(PageableUtils.DEFAULT_SIZE);
        assertThat(result.getSort().getOrderFor("updatedAt")).isNotNull();
        assertThat(result.getSort().getOrderFor("updatedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(result.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void sizeAboveMax_isClamped() {
        Pageable result = PageableUtils.normalize(PageRequest.of(0, 5000));
        assertThat(result.getPageSize()).isEqualTo(PageableUtils.MAX_SIZE);
    }

    @Test
    void explicitSort_isPreservedWithIdTiebreaker() {
        Pageable result = PageableUtils.normalize(PageRequest.of(2, 10, Sort.by(Sort.Order.asc("title"))));

        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(result.getSort().getOrderFor("id")).isNotNull();
        // default updatedAt sort must NOT be added when an explicit sort is present
        assertThat(result.getSort().getOrderFor("updatedAt")).isNull();
    }

    @Test
    void existingIdSort_notDuplicated() {
        Pageable result = PageableUtils.normalize(PageRequest.of(0, 10, Sort.by(Sort.Order.desc("id"))));
        long idOrders = result.getSort().stream().filter(o -> o.getProperty().equals("id")).count();
        assertThat(idOrders).isEqualTo(1);
        assertThat(result.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
