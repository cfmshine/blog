package blog.mapper;

import blog.model.User;
import java.util.List;

/**
 * Created by ChenFumin on 2016-9-20.
 */
public interface UserMapper {
  List<User> queryByPage(User uservo);
}
