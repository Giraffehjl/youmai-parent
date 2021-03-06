package com.youmai.manager.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.youmai.pojo.TbSpecification;
import com.youmai.pojogroup.Specification;
import com.youmai.sellergoods.service.SpecificationService;
import entity.PageResult;
import entity.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @ClassName: specificationController
 * @Description: 规格Controller
 * @Author: 泊松
 * @Date: 2018/9/12 20:15
 * @Version: 1.0
 */
@RestController
@RequestMapping("/specification")
public class SpecificationController {

    @Reference
    private SpecificationService specificationService;

    @RequestMapping("/findAll")
    public PageResult findAll(Integer page,Integer size){
        return specificationService.findAll(page,size);
    }

    @RequestMapping("/add")
    public Result add(@RequestBody Specification specification){
        try {
            specificationService.add(specification);
            return new Result(true,"添加成功");
        } catch (Exception e) {
            e.printStackTrace();
            return  new Result(false,"添加失败了呢");
        }
    }

    @RequestMapping("/findOne")
    public Specification findOne(Long id){
        return specificationService.findOne(id);
    }

    @RequestMapping("/update")
    public Result update(@RequestBody Specification specification){
        try {
            specificationService.update(specification);
            return new Result(true,"修改成功");
        } catch (Exception e) {
            e.printStackTrace();
            return  new Result(false,"修改失败了呢");
        }
    }

    @RequestMapping("/delete")
    public Result delete(Long[] ids){
        try {
            specificationService.delete(ids);
            return new Result(true,"删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return  new Result(false,"删除失败了呢");
        }
    }

    @RequestMapping("/selectOptionList")
    public List<Map> selectOptionList(){
        return specificationService.selectOptionList();
    }
}
