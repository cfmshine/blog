package blog.test;

import blog.mapper.UserMapper;
import blog.model.Page;
import blog.model.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
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
public class MapperTest {

  @Resource
  private SqlSessionFactory sqlSessionFactory;

  @Test
  public void testMapper() {
    SqlSession sqlSession = sqlSessionFactory.openSession();
    UserMapper mapper = sqlSession.getMapper(UserMapper.class);
    User uservo = new User();
    uservo.setUsername("admin");
    Page page = new Page<User>();
    page.setPageNo(1);
    Map map = new HashMap();
    map.put("username", "admin");
    page.setParams(map);
    List<User> users = (List<User>) mapper.queryByPage(uservo);
    log.info("【查询成功】" + users);
  }

}
