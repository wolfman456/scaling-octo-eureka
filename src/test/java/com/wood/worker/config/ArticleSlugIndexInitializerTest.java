package com.wood.worker.config;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleSlugIndexInitializerTest {

    private JdbcTemplate jdbc;
    private ArticleSlugIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:sluginit" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table article (id bigint primary key, slug varchar(200) not null)");
        jdbc.update("insert into article (id, slug) values (1, 'one')");
        initializer = new ArticleSlugIndexInitializer(jdbc);
    }

    private int slugIndexCount() {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.indexes where table_name = 'ARTICLE' and index_name = 'UQ_ARTICLE_SLUG'",
                Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    void createsUniqueIndexWhenMissing() {
        initializer.ensureIndex();
        assertEquals(1, slugIndexCount());
    }

    @Test
    void skipsWhenIndexAlreadyExists() {
        jdbc.execute("create unique index uq_article_slug on article (slug)");
        initializer.ensureIndex();
        assertEquals(1, slugIndexCount());
    }

    @Test
    void doesNotFailWhenDuplicateSlugsPreventIndex() {
        jdbc.update("insert into article (id, slug) values (2, 'one')");
        initializer.ensureIndex();
        assertEquals(0, slugIndexCount());
    }
}