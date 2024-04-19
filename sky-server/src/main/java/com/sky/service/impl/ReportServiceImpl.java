package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //当前集合用于存放从begin到end范围内的每天天数
        List<LocalDate> dateList=new ArrayList<>();
        
        dateList.add(begin);

        while (!begin.equals(end)){
            //日期计算,计算指定日期的后一天对应的日期
            begin=begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList=new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询date对应营业额数据,营业额是指:状态"已完成"的订单金额总计
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);


                //select sum(amount) form orders where oreder_time<? and status=5
            Map map=new HashMap();
            map.put("begin",beginTime);
            map.put("end",endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover=orderMapper.sumByMap(map);
            turnover= turnover==null?0.0:turnover;
            turnoverList.add(turnover);
            
        }


        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .turnoverList(StringUtils.join(turnoverList,","))
                .build();
    }

    /**
     * 统计指定时间区间内的用户数据
     * @param begin
     * @param end
     * @return
     */

    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end的每天对应日期
        List<LocalDate> dateList=new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天新增用户数量 selcet count(id) from user where creat_time < ? and create_time>?
        List<Integer> newUserList=new ArrayList<>();

        //存放每天的总数量selcet count(id) from user where creat_time<?
        List<Integer> totalUserList=new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);


            Map map=new HashMap();
            map.put("end",endTime);

            //总用户数量
            Integer totalUser = userMapper.countByMap(map);

            map.put("begin",beginTime);
            //新增用户数量
            Integer newUser= userMapper.countByMap(map);


            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }



        //封装结果数据
        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList,","))
                .totalUserList(StringUtils.join(totalUserList,","))
                .newUserList(StringUtils.join(newUserList,","))
                .build();
    }

    /**
     * 统计指定时间区间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end的每天对应日期
        List<LocalDate> dateList=new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放每天的订单总数
        List<Integer> orderCountList=new ArrayList<>();
        //存放每天的有效订单数
        List<Integer> validOrderCountList=new ArrayList<>();


        //遍历datelist集合 查询每天有效的订单数和订单总量
        for (LocalDate date : dateList) {
            //查询每天的订单总数 selcet count(id) from orders where order_time >? and order_Time<?
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            //查询每天的有效订单数 selcet count(id) from orders where order_time >? and order_Time<? and status=?
            Integer validorderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validorderCount);
        }


        //计算时间区间内的订单总数量
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        //计算时间区间内的有效订单数量
        Integer vaildOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        //计算订单完成率
        Double orderCompletionRate=0.0;
        if(totalOrderCount!=0){
            //计算订单完成率
            orderCompletionRate=vaildOrderCount.doubleValue() /totalOrderCount;
        }


        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(vaildOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }


    private Integer getOrderCount(LocalDateTime begin,LocalDateTime end,Integer status){
        Map map = new HashMap();
        map.put("begin",begin);
        map.put("end",end);
        map.put("status",status);

        return orderMapper.countByMap(map);

    }

    /**
     * 统计指定区域内的销量排名前10
     * @param begin
     * @param end
     * @return
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin,LocalDate end){
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");

        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        //封装返回结果数据
        return SalesTop10ReportVO
                .builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出运营数据报表
     * @param response
     */
    public void exportBusinessData(HttpServletResponse response) throws IOException {
        //1.查询数据库,获取营业额
        LocalDate beginTime = LocalDate.now().minusDays(30);
        LocalDate endTime = LocalDate.now().minusDays(1);

        BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(beginTime, LocalTime.MIN), LocalDateTime.of(endTime, LocalTime.MAX));
        //2.通过poi将数据写入到excal
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/moban.xlsx");
        //基于模板文件创建一个新的excal文件
        try {
            XSSFWorkbook excel = new XSSFWorkbook(in);


            //获取表格文件的sheet页
            XSSFSheet sheet = excel.getSheet("sheet1");
            //填充数据--时间
            sheet.getRow(1).getCell(1).setCellValue("时间"+beginTime+"至"+endTime);

            //获取第四行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessData.getTurnover());
            row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessData.getNewUsers());

            //获取第五行
           row= sheet.getRow(4);
           row.getCell(2).setCellValue(businessData.getValidOrderCount());
           row.getCell(4).setCellValue(businessData.getUnitPrice());


           //填充数据
            for (int i =0;i<30;i++){
                LocalDate date = beginTime.plusDays(i);
                //查询某一天的营业数据
                BusinessDataVO businessData1 = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                //获取某一行
                 row = sheet.getRow(7 + i);
                 row.getCell(1).setCellValue( date.toString());
                 row.getCell(2).setCellValue(businessData1.getTurnover());
                 row.getCell(3).setCellValue(businessData1.getValidOrderCount());
                 row.getCell(4).setCellValue(businessData1.getOrderCompletionRate());
                 row.getCell(5).setCellValue(businessData1.getUnitPrice());
                 row.getCell(6).setCellValue(businessData1.getNewUsers());

            }

//3.通过输出流将excal输出到浏览器中
        ServletOutputStream outputStream = response.getOutputStream();
        excel.write(outputStream);

        //关闭资源
        outputStream.close();
        excel.close();

        }catch (IOException e){
            e.printStackTrace();
        }




    }
}
