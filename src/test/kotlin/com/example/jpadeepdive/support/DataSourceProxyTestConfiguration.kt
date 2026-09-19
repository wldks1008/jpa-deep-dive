package com.example.jpadeepdive.support

import net.ttddyy.dsproxy.listener.QueryExecutionListener
import net.ttddyy.dsproxy.support.ProxyDataSource
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.mockito.Mockito.mock
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Role
import javax.sql.DataSource

// @Import한 테스트에서만 적용. 엔티티/Repository 스캔은 Spring Boot 기본 설정을 사용한다.
@TestConfiguration(proxyBeanMethods = false)
class DataSourceProxyTestConfiguration {
    companion object {
        @Bean
        @JvmStatic
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        fun queryExecutionListener(): QueryExecutionListener = mock(QueryExecutionListener::class.java)

        // BeanPostProcessor는 일반 Bean보다 먼저 생성되므로 static @Bean으로 등록한다.
        @Bean
        @JvmStatic
        fun dataSourceProxyPostProcessor(listener: QueryExecutionListener): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessAfterInitialization(bean: Any, beanName: String): Any =
                    if (bean is DataSource && bean !is ProxyDataSource) {
                        ProxyDataSourceBuilder.create(bean)
                            .name(beanName)
                            .countQuery() // QueryCountHolder에 SQL 종류별 횟수를 집계한다.
                            .logQueryToSysOut() // SQL과 파라미터를 실행 순서대로 출력한다.
                            .listener(listener) // Mockito로 실제 실행 이벤트 순서를 검증한다.
                            .build()
                    } else {
                        bean
                    }
            }
    }
}
