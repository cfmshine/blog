package blog.test;

import java.sql.Connection;
import java.sql.SQLException;
import javax.annotation.Resource;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created by ChenFumin on 2016-9-20.
 */
@RunWith(value = SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/spring-dao.xml"})
@Slf4j
public class ConnectionTest {

  @Resource
  private DataSource dataSource;

  @Test
  public void testConnection() throws SQLException {
    Connection connection = dataSource.getConnection();
    log.info("获取数据库连接成功: " + connection);
  }

}
