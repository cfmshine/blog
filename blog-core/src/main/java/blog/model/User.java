package blog.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by ChenFumin on 2016-9-20.
 */
@Setter
@Getter
@ToString
public class User extends Entity<User> {
  private String username;
  private String password;
  private String avatar;
  private String name;
  private String email;
  private String mobile;
}
