package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author TanH
 * @since 2025-12-18
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery);

    LearningLessonVO queryNowLearning();

    LearningLessonVO queryCourseStatus(Long courseId);

    void removeInvalidCourses(Long courseId);

    void removeInvalidCoursesFromMQ(Long userId, List<Long> courseIds);

    Long queryCourseValid(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);
}
