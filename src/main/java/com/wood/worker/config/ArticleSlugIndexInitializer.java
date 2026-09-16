package com.wood.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ArticleSlugIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArticleSlugIndexInitializer.class);

    private final JdbcTemplate jdbc;

    public ArticleSlugIndexInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex();
    }

    void ensureIndex() {
        Integer constraints = jdbc.queryForObject("""
                select count(*)
                from information_schema.table_constraints c
                join information_schema.constraint_column_usage u
                  on u.constraint_name = c.constraint_name and u.table_schema = c.table_schema
                where c.table_schema = 'public' and c.table_name = 'article'
                  and c.constraint_type = 'UNIQUE' and u.column_name = 'slug'
                """, Integer.class);
        if (constraints != null && constraints > 0) {
            return;
        }
        try {
            jdbc.execute("create unique index uq_article_slug on article (slug)");
            log.info("Created unique index uq_article_slug on article.slug");
        } catch (DataAccessException e) {
            log.warn("Could not create unique index uq_article_slug (existing duplicate slugs?): {}",
                    e.getMessage());
        }
    }
}