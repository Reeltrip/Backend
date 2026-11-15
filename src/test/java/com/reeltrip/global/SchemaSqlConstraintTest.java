package com.reeltrip.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 운영 DB는 docs/schema.sql을 손으로 적용하고 ddl-auto=validate로 켜요. validate는 유니크·인덱스를 보지 않아서,
 * 엔티티에 선언한 제약이 schema.sql에 빠져도 서버는 떠요(#56: 멤버 중복 → 계획 열기 500). 그래서 이름으로 대조해요.
 */
class SchemaSqlConstraintTest {

    @Test
    @DisplayName("엔티티에 선언한 유니크 제약과 인덱스는 모두 docs/schema.sql에도 있다.")
    void schemaSqlHasEveryEntityConstraint() throws Exception {
        // given
        // Docker 빌드(CI)는 docs/를 이미지에 넣지 않아서 파일이 없어요. 그때는 건너뛰고 로컬 ./gradlew test에서 검사해요.
        Path schemaPath = Path.of("docs/schema.sql");
        assumeTrue(Files.exists(schemaPath), "docs/schema.sql이 없는 빌드(Docker)라 건너뜀");
        String schema = Files.readString(schemaPath);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        // when
        List<String> missing = new ArrayList<>();
        for (var bean : scanner.findCandidateComponents("com.reeltrip")) {
            Table table = Class.forName(bean.getBeanClassName()).getAnnotation(Table.class);
            if (table == null) {
                continue;
            }
            for (UniqueConstraint unique : table.uniqueConstraints()) {
                if (!schema.contains("`" + unique.name() + "`")) {
                    missing.add(table.name() + "." + unique.name());
                }
            }
            for (Index index : table.indexes()) {
                if (!schema.contains("`" + index.name() + "`")) {
                    missing.add(table.name() + "." + index.name());
                }
            }
        }

        // then
        assertThat(missing).as("schema.sql에 없는 제약·인덱스").isEmpty();
    }
}
