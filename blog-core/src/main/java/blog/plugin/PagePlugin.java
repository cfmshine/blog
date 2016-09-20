package blog.plugin;

import blog.model.Page;
import blog.utils.ReflectHelper;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.xmltags.ForEachSqlNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * Created by ChenFumin on 2016-9-1.
 */
@Intercepts({
    @Signature(method = "prepare", type = StatementHandler.class, args = {Connection.class,
        Integer.class})})
public class PagePlugin implements Interceptor {

  /**
   * 数据库类型
   */
  private String dialect;
  private String pageId;

  public Object intercept(Invocation invocation) throws Throwable {

    Object handler = invocation.getTarget();

    if (handler instanceof RoutingStatementHandler) {
      StatementHandler delegate =
          (StatementHandler) ReflectHelper.getValueByFieldName(handler, "delegate");
      BoundSql boundSql = delegate.getBoundSql();
      Object parameter = boundSql.getParameterObject();
      Page<?> page = null;
      MappedStatement mappedStatement =
          (MappedStatement) ReflectHelper.getValueByFieldName(delegate, "mappedStatement");

      if (mappedStatement.getId().matches(pageId)) {

        Connection connection = (Connection) invocation.getArgs()[0];
        String sql = boundSql.getSql();
        int count = this.setTotalRecord(page, mappedStatement, connection, parameter);

        if (parameter instanceof Page<?>) {
          page = (Page<?>) parameter;
          page.setTotalRecord(count);
        } else {
          Field pageField = ReflectHelper.getFieldByFieldName(parameter, "page");
          if (pageField != null) {
            page = (Page) ReflectHelper.getValueByFieldName(parameter, "page");
            if (page == null) {
              page = new Page();
            }
            page.setTotalRecord(count);
          }
          ReflectHelper.setValueByFieldName(parameter, "page", page); //通过反射，对实体对象设置分页对象
        }
        return invocation.proceed();
      }
    }
    return null;
  }


  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  public void setProperties(Properties properties) {
    this.dialect = properties.getProperty("dialect");
    this.pageId = properties.getProperty("pageId");
  }

  /**
   * 给当前的参数对象page设置总记录数
   *
   * @param page Mapper映射语句对应的参数对象
   * @param mappedStatement Mapper映射语句
   * @param connection 当前的数据库连接
   */
  private int setTotalRecord(Page<?> page, MappedStatement mappedStatement,
      Connection connection, Object parameter) {

    int count = 0;

    // 获取对应的BoundSql，这个BoundSql其实跟我们利用StatementHandler获取到的BoundSql是同一个对象。
    // delegate里面的boundSql也是通过mappedStatement.getBoundSql(paramObj)方法获取到的。
    BoundSql boundSql = mappedStatement.getBoundSql(page);
    // 获取到我们自己写在Mapper映射语句中对应的Sql语句
    String sql = boundSql.getSql();
    // 通过查询Sql语句获取到对应的计算总记录数的sql语句
    String countSql = this.getCountSql(sql);
    // 通过BoundSql获取对应的参数映射
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    // 利用Configuration、查询记录数的Sql语句countSql、参数映射关系parameterMappings和参数对象page建立查询记录数对应的BoundSql对象。
    BoundSql countBoundSql =
        new BoundSql(mappedStatement.getConfiguration(), countSql, parameterMappings, page);
    // 通过mappedStatement、参数对象page和BoundSql对象countBoundSql建立一个用于设定参数的ParameterHandler对象
    ParameterHandler parameterHandler =
        new DefaultParameterHandler(mappedStatement, page, countBoundSql);
    // 通过connection建立一个countSql对应的PreparedStatement对象。
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try {
      pstmt = connection.prepareStatement(countSql);
      // 通过parameterHandler给PreparedStatement对象设置参数
      parameterHandler.setParameters(pstmt);
      setParameters(pstmt, mappedStatement, boundSql, parameter);
      // 之后就是执行获取总记录数的Sql语句和获取结果了。
      rs = pstmt.executeQuery();
      if (rs.next()) {
        count = rs.getInt(1);
        // 给当前的参数page对象设置总记录数
        // page.setTotalRecord(totalRecord);
      }
      return count;
    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (rs != null) {
          rs.close();
        }
        if (pstmt != null) {
          pstmt.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return count;
  }

  private void setParameters(PreparedStatement ps, MappedStatement mappedStatement,
      BoundSql boundSql, Object parameterObject) throws SQLException {
    ErrorContext.instance()
        .activity("setting parameters")
        .object(mappedStatement.getParameterMap().getId());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings != null) {
      Configuration configuration = mappedStatement.getConfiguration();
      TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      MetaObject metaObject =
          parameterObject == null ? null : configuration.newMetaObject(parameterObject);
      for (int i = 0; i < parameterMappings.size(); i++) {
        ParameterMapping parameterMapping = parameterMappings.get(i);
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          String propertyName = parameterMapping.getProperty();
          PropertyTokenizer prop = new PropertyTokenizer(propertyName);
          if (parameterObject == null) {
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
          } else if (boundSql.hasAdditionalParameter(propertyName)) {
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (propertyName.startsWith(ForEachSqlNode.ITEM_PREFIX)
              && boundSql.hasAdditionalParameter(prop.getName())) {
            value = boundSql.getAdditionalParameter(prop.getName());
            if (value != null) {
              value = configuration.newMetaObject(value)
                  .getValue(propertyName.substring(prop.getName().length()));
            }
          } else {
            value = metaObject == null ? null : metaObject.getValue(propertyName);
          }
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          if (typeHandler == null) {
            throw new ExecutorException("There was no TypeHandler found for parameter "
                + propertyName
                + " of statement "
                + mappedStatement.getId());
          }
          typeHandler.setParameter(ps, i + 1, value, parameterMapping.getJdbcType());
        }
      }
    }
  }

  /**
   * 根据原Sql语句获取对应的查询总记录数的Sql语句
   */
  private String getCountSql(String sql) {
    String fromKeyword = findKeyword(sql, "from");
    if (fromKeyword == null) {
      throw new RuntimeException("NotFoundException, SQLException, Can't find \"FROM\" keyword");
    }
    int index = sql.toUpperCase().indexOf(fromKeyword);
    return "SELECT COUNT(*) " + sql.substring(index);
  }

  /**
   * 查找关键字
   */
  private String findKeyword(String sql, String keyword) {
    Pattern pattern = Pattern.compile("\\s+" + keyword.toUpperCase() + "\\s+");
    String upperCaseSql = sql.toUpperCase();
    Matcher matcher = pattern.matcher(upperCaseSql);
    if (matcher.find()) {
      return matcher.group();
    }
    return null;
  }

  /**
   * 根据page对象获取对应的分页查询Sql语句，这里只做了两种数据库类型，Mysql和Oracle 其它的数据库都 没有进行分页
   *
   * @param page 分页对象
   * @param sql 原sql语句
   */
  private String getPageSql(Page<?> page, String sql) {
    StringBuffer sqlBuffer = new StringBuffer(sql);
    if ("mysql".equalsIgnoreCase(dialect)) {
      return getMysqlPageSql(page, sqlBuffer);
    } else if ("oracle".equalsIgnoreCase(dialect)) {
      return getOraclePageSql(page, sqlBuffer);
    }
    return sqlBuffer.toString();
  }

  /**
   * 获取Mysql数据库的分页查询语句
   *
   * @param page 分页对象
   * @param sqlBuffer 包含原sql语句的StringBuffer对象
   * @return Mysql数据库分页语句
   */
  private String getMysqlPageSql(Page<?> page, StringBuffer sqlBuffer) {
    //计算第一条记录的位置，Mysql中记录的位置是从0开始的。
    int offset = (page.getPageNo() - 1) * page.getPageSize();
    sqlBuffer.append(" limit ").append(offset).append(",").append(page.getPageSize());
    return sqlBuffer.toString();
  }

  /**
   * 获取Oracle数据库的分页查询语句
   *
   * @param page 分页对象
   * @param sqlBuffer 包含原sql语句的StringBuffer对象
   * @return Oracle数据库的分页查询语句
   */
  private String getOraclePageSql(Page<?> page, StringBuffer sqlBuffer) {
    //计算第一条记录的位置，Oracle分页是通过rownum进行的，而rownum是从1开始的
    int offset = (page.getPageNo() - 1) * page.getPageSize() + 1;
    sqlBuffer.insert(0, "select u.*, rownum r from (")
        .append(") u where rownum < ")
        .append(offset + page.getPageSize());
    sqlBuffer.insert(0, "select * from (").append(") where r >= ").append(offset);
    //上面的Sql语句拼接之后大概是这个样子：
    //select * from (select u.*, rownum r from (select * from t_user) u where rownum < 31) where r >= 16
    return sqlBuffer.toString();
  }
}
