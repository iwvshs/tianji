package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author TanH
 * @since 2025-12-18
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表相关接口")
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    public LearningLessonController(ILearningLessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery){
        if (pageQuery == null) {
            return null;
        }
        return lessonService.queryMyLessons(pageQuery);
    }

    @GetMapping("/now")
    @ApiOperation("查询当前正在学习的课程")
    public LearningLessonVO queryNowLearning(){
        return lessonService.queryNowLearning();
    }

    //根据课程id查询课程状态
    @GetMapping("/{courseId}")
    @ApiOperation("根据课程id查询课程状态")
    public LearningLessonVO queryCourseStatus(@PathVariable Long courseId){
        return lessonService.queryCourseStatus(courseId);
    }

    //直接删除已失效课程
    @DeleteMapping("/{courseId}")
    @ApiOperation("直接根据courseId删除课表中失效的课程")
    public void deleteInvalidCourses(@PathVariable Long courseId){
        lessonService.removeInvalidCourses(courseId);
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("根据课程id查询课程是否有效")
    public Long queryCourseValid(@PathVariable Long courseId){
        return lessonService.queryCourseValid(courseId);
    }

    //统计课程的学习人数
    @GetMapping("/{courseId}/count")
    @ApiOperation("统计课程的学习人数")
    public Long countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }



}
