package com.jkmvc.db

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.sql.DataSource


/**
 * 封装db操作
 */
class Db:IDb{

    /**
     * 数据库连接
     */
    lateinit protected var conn: Connection;

    /**
     * 当前事务的嵌套层级
     */
    protected var transDepth:Int = 0;

    /**
     * 标记当前事务是否回滚
     */
    protected var rollbacked = false;

    protected constructor(conn: Connection){
        this.conn = conn;
        threadLocal.set(this);
    }

    companion object {
        protected val threadLocal:ThreadLocal<Db> = ThreadLocal<Db>()

        /**
         * 获得当前数据库连接
         */
        public fun current():Db {
            return threadLocal.get()!!;
        }

        /**
         * 连接数据库
         */
        public fun connect(datasource: DataSource): Db {
            return Db(datasource.connection)
        }

        /**
         * 连接数据库
         */
        public fun connect(url: String, driver: String, user: String = "", password: String = ""): Db {
            Class.forName(driver).newInstance()
            return Db(DriverManager.getConnection(url, user, password));
        }

        /**
         * 关闭数据库连接
         */
        public fun close(){
            threadLocal.get()?.close();
        }
    }

    /**
     * 执行事务
     */
    public override fun <T> transaction(statement: Db.() -> T):T{
        try{
            begin(); // 开启事务
            val result:T = this .statement(); // 执行sql
            commit(); // 提交事务
            return result; // 返回结果
        }catch(e:Exception){
            rollback(); // 回顾
            throw e;
        }finally{
            close() // 关闭连接
        }
    }

    /**
     * 执行更新
     */
    public override fun execute(sql: String, paras: List<Any?>?): Int {
        return conn.execute(sql, paras);
    }

    /**
     * 查询多行
     */
    public override fun <T> queryResult(sql: String, paras: List<Any?>?, action: (ResultSet) -> T): T {
        return conn.queryResult(sql, paras, action)
    }

    /**
     * 查询多行
     */
    public override fun <T> queryRows(sql: String, paras: List<Any?>?, transform: (MutableMap<String, Any?>) -> T): List<T> {
        return conn.queryRows(sql, paras, transform);
    }

    /**
     * 查询一行(多列)
     */
    public override fun <T> queryRow(sql: String, paras: List<Any?>?, transform: (MutableMap<String, Any?>) -> T): T? {
        return conn.queryRow(sql, paras, transform);
    }

    /**
     * 查询一行一列
     */
    public override fun queryCell(sql: String, paras: List<Any?>?): Pair<Boolean, Any?> {
        return conn.queryCell(sql, paras);
    }

    /**
     * 开启事务
     */
    public override fun begin():Unit{
        if(transDepth++ === 0)
            conn.autoCommit = false; // 禁止自动提交事务
    }

    /**
     * 提交事务
     */
    public override fun commit():Boolean{
        // 未开启事务
        if (transDepth <= 0)
        return false;

        // 无嵌套事务
        if (--transDepth === 0)
        {
            // 回滚 or 提交事务: 回滚的话,返回false
            if(rollbacked)
                conn.rollback();
            else
                conn.commit()
            val result = rollbacked;
            rollbacked = false; // 清空回滚标记
            return result;
        }

        // 有嵌套事务
        return true;
    }

    /**
     * 回滚事务
     */
    public override fun rollback():Boolean{
        // 未开启事务
        if (transDepth <= 0)
            return false;

        // 无嵌套事务
        if (--transDepth === 0)
        {
            rollbacked = false; // 清空回滚标记
            conn.rollback(); // 回滚事务
        }

        // 有嵌套事务
        rollbacked = true; // 标记回滚
        return true;
    }

    /**
     * 关闭
     */
    public override fun close():Unit{
        conn.close();
        threadLocal.set(null);
    }

    /**
     * 转义多个表名
     *
     * @param Collection<Any> tables 表名集合，其元素可以是String, 也可以是Pair<表名, 别名>
     * @return string
     */
    public override fun quoteTables(tables:Collection<Any>, with_brackets:Boolean):String
    {
        // 遍历多个表转义
        val str:StringBuilder = StringBuilder();
        for (item in tables)
        {
            var table:String;
            var alias:String?;
            if(item is Pair<*, *>){ // 有别名
                table = item.component1() as String;
                alias = item .component2() as String;
            }else{ // 无别名
                table = item as String;
                alias = null;
            }
            // 单个表转义
            str.append(quoteTable(table, alias)).append(", ");
        }
        str.delete(", ");
        return if(with_brackets)  "($str)" else str.toString();
    }

    /**
     * 转义表名
     *
     * @param string table
     * @return string
     */
    public override fun quoteTable(table:String, alias:String?):String
    {
        return if(alias == null)
            "`$table`";
        else
            "`$table`  AS `$alias`"
    }

    /**
     * 转义多个字段名
     *
     * @param Collection<Any> columns 表名集合，其元素可以是String, 也可以是Pair<字段名, 别名>
     * @param bool with_brackets 当拼接数组时, 是否用()包裹
     * @return string
     */
    public override fun quoteColumns(columns:Collection<Any>, with_brackets:Boolean):String
    {
        // 遍历多个字段转义
        val str:StringBuilder = StringBuilder();
        for (item in columns)
        {
            var column:String;
            var alias:String?;
            if(item is Pair<*, *>){ // 有别名
                column = item.component1() as String;
                alias = item .component2() as String;
            }else{ // 无别名
                column = item as String;
                alias = null;
            }

            // 单个字段转义
            str.append(quoteColumn(column, alias)).append(", ");
        }

        // 删最后逗号
        str.delete(", ");

        // 加括号
        if(with_brackets)
            str.insert(0, '(').append(')');

        return str.toString();
    }

    /**
     * 转义字段名
     *
     * @param string|array column 字段名, 可以是字段数组
     * @param string alias 字段别名
     * @param bool with_brackets 当拼接数组时, 是否用()包裹
     * @return string
     */
    public override fun quoteColumn(column:String, alias:String?, with_brackets:Boolean):String
    {
        var table = "";
        var col = column;

        // 非函数表达式
        if ("^\\w[\\w\\d_\\.]*".toRegex().matches(column))
        {
            // 表名
            if(column.contains('.')){
                var arr = column.split('.');
                table = "`${arr[0]}`.";
                col = arr[1]
            }

            // 字段名
            if(column != "*") // 非*
                col = "`$col`"; // 转义
        }

        // 字段别名
        if(alias == null)
            return table + col;

        return table + col + " AS `$alias`"; // 转义
    }

    /**
     * 转义值
     *
     * @param string|array value 字段值, 可以是值数组
     * @return string
     */
    public override fun quote(values:Collection<Any?>):String
    {
        val str:StringBuffer = StringBuffer();
        return values.map {
            quote(it);
        }.joinToString(", ", "(", ")").toString() // 头部 + 连接符拼接多值 + 尾部
    }

    /**
     * 转义值
     *
     * @param string|array value 字段值, 可以是值数组
     * @return Any
     */
    public override fun quote(value:Any?):Any?
    {
        // null => "null"
        if(value == null)
            return "null";

        // bool => int
        if(value is Boolean)
            return if(value) 1 else 0;

        // int/float
        if(value is Number)
            return value;

        // 非string => string
        return value.toString();
    }
}
