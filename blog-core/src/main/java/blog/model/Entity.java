package blog.model;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by ChenFumin on 2016-9-20.
 */
@Setter
@Getter
public class Entity<T> implements Serializable{
  private Integer id;
  private Page<T> page = new Page<T>();
}
