package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author TanH
 * @since 2025-12-18
 */
@Service
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    public static final int PENDING = 1;
    public static final int REMOVED = 3;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;

    public LearningLessonServiceImpl(CourseClient courseClient, CatalogueClient catalogueClient) {
        this.courseClient = courseClient;
        this.catalogueClient = catalogueClient;
    }

    /**
     * @description: 添加用户课程到课表
     * @param userId 用户id
     * @param courseIds 课程id集合
     * @return void
     * @author: TanH
     * @date: 2025/12/26
     **/
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //1. 根据id集合查询出课程信息
        List<CourseSimpleInfoDTO> courseInfoList = courseClient.getSimpleInfoList(courseIds);

        if (CollUtils.isEmpty(courseInfoList)) {
            log.error("课程信息为空，无法添加课表");
            return;
        }

        //2. 循环遍历，封装LearningLesson
        List<LearningLesson> learningLessons = new ArrayList<>(courseInfoList.size()); //防止自动扩容
        for (CourseSimpleInfoDTO courseInfo : courseInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 2.1 计算并填入过期时间
            Integer validDuration = courseInfo.getValidDuration();
            if (validDuration != null && validDuration > 0) { //有效期不为空才添加数据
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expireTime = now.plusMonths(validDuration);
                lesson.setExpireTime(expireTime); // 设置过期时间
            }
            // 2.2 填入userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(courseInfo.getId());

            //2.3 加入课表list
            learningLessons.add(lesson);
        }
        //3. 批量保存LearningLesson
        saveBatch(learningLessons);

    }


    /**
     * @description: 查询我的全部课程，并添加到我的课表中
     * @param pageQuery 分页参数
     * @return com.tianji.common.domain.dto.PageDTO<com.tianji.learning.domain.vo.LearningLessonVO>
     * @author: TanH
     * @date: 2025/12/21
     **/
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery pageQuery) {
        //1. 获取当前用户信息
        Long userId = UserContext.getUser();

        //2. 根据用户id查询出课程信息
        //select * from learning_lesson where user_id = ? order by latest_learn_time limit pageNo, pageSize;
        Page<LearningLesson> lessonPage = baseMapper.selectPage(
                pageQuery.toMpPage("latest_learn_time", false),
                new QueryWrapper<LearningLesson>().eq("user_id", userId)
        );

        //2.1 判断课程信息是否为空
        List<LearningLesson> lessonList = lessonPage.getRecords();
        if (CollUtils.isEmpty(lessonList)) {
            return PageDTO.empty(lessonPage);
        }
        log.info("查询结果：{}", lessonList);

        //3. 获取列表中的课程id，收集成set集合，防止课程id重复
        Set<Long> courseIds = lessonList.stream()
                .map(LearningLesson::getCourseId)
                .collect(Collectors.toSet());

        //4. 要调用课程微服务来获取课程信息
        List<CourseSimpleInfoDTO> courseInfos = courseClient.getSimpleInfoList(courseIds);

        //4.1 收集成key为课程id，value为课程信息的map
        Map<Long, CourseSimpleInfoDTO> courseInfoMap =
                courseInfos.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //5. 循环遍历，将课程信息封装成VO对象，并添加到列表中
        List<LearningLessonVO> learningLessonVOList = new ArrayList<>(courseInfos.size());
        for (LearningLesson lesson : lessonList) {
            //5.1 创建VO对象，添加基本信息
            LearningLessonVO learningLessonVO = BeanUtils.copyBean(lesson, LearningLessonVO.class);
            //5.2 添加课程名称和封面
            CourseSimpleInfoDTO course = courseInfoMap.get(lesson.getCourseId());
            learningLessonVO.setCourseCoverUrl(course.getCoverUrl()); // 设置课程封面
            learningLessonVO.setCourseName(course.getName()); //设置课程名称
            learningLessonVO.setSections(course.getSectionNum());
            //5.3 添加到列表中
            learningLessonVOList.add(learningLessonVO);
        }

        //6. 封装成PageDTO，of方法设置分页的参数，以及课程信息数据
        PageDTO<LearningLessonVO> pageDTO = PageDTO.of(lessonPage, learningLessonVOList);
        return pageDTO;
    }

    /**
     * @description: 查询正在学习的课程
     * @param
     * @return com.tianji.learning.domain.vo.LearningLessonVO
     * @author: TanH
     * @date: 2025/12/25
     **/
    @Override
    public LearningLessonVO queryNowLearning() {
        Long userId = UserContext.getUser();

        //1. 根据用户id查询正在学习的课程
        LearningLesson learningLesson = baseMapper.selectOne(
                new QueryWrapper<LearningLesson>()
                        .eq("user_id", userId)
                        .eq("status", LessonStatus.LEARNING.getValue())
                        .orderByDesc("latest_learn_time")
                        .last("limit 1")
        );
        if (learningLesson == null) {
            return null;
        }
        //2. 将learningLesson复制到learningLessonVO对象中
        LearningLessonVO learningLessonVO = BeanUtils.copyBean(learningLesson, LearningLessonVO.class);

        //3. 调用课程微服务，获取课程信息
        CourseFullInfoDTO courseInfoDTO = courseClient.getCourseInfoById(learningLesson.getCourseId(), false, false);
        if (courseInfoDTO == null) {
            return null;
        }

        //4. 设置courseName、courseCoverUrl、sections字段
        learningLessonVO.setCourseName(courseInfoDTO.getName());
        learningLessonVO.setCourseCoverUrl(courseInfoDTO.getCoverUrl());
        learningLessonVO.setSections(courseInfoDTO.getSectionNum());

        //5. 统计课表中的课程数量，并设置到VO对象中
        Integer courseNum = baseMapper.selectCount(
                new QueryWrapper<LearningLesson>()
                        .eq("user_id", userId)
        );
        learningLessonVO.setCourseAmount(courseNum);

        //6. 获取最新学习小节名称和序号
        List<Long> sectionIdList = CollUtils.singletonList(learningLesson.getLatestSectionId());
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(sectionIdList);
        //7. 不为空，则设置最新学习小节名称和序号
        if (!CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
            learningLessonVO.setLatestSectionName(cataSimpleInfoDTO.getName());
            learningLessonVO.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());
        }

        return learningLessonVO;
    }

    /**
     * @description: 根据课程id查询课程状态
     * @param courseId
     * @return com.tianji.learning.domain.vo.LearningLessonVO
     * @author: TanH
     * @date: 2025/12/26
     **/
    @Override
    public LearningLessonVO queryCourseStatus(Long courseId) {
        LearningLesson learningLesson = baseMapper.selectOne(
                new QueryWrapper<LearningLesson>()
                        .eq("course_id", courseId)
                        .eq("user_id", UserContext.getUser())
        );

        if (learningLesson == null) {
            return null;
        }

        return BeanUtils.copyBean(learningLesson, LearningLessonVO.class);
    }

    // 根据课程id删除课表中失效的课程
    @Override
    public void removeInvalidCourses(Long courseId) {

        this.remove(new QueryWrapper<LearningLesson>()
                .eq("course_id", courseId)
                .eq("user_id", UserContext.getUser()));
    }

    // 获取MQ中的通知消息，删除课表中退款课程
    @Override
    public void removeInvalidCoursesFromMQ(Long userId, List<Long> courseIds) {
        this.remove(new QueryWrapper<LearningLesson>()
                .eq("user_id", userId)
                .in("course_id", courseIds));
    }

    // 查询课表中是否有课程，并查询课程状态是否有效
    @Override
    public Long queryCourseValid(Long courseId) {
        LearningLesson learningLesson = baseMapper.selectOne(
                new QueryWrapper<LearningLesson>()
                        .eq("course_id", courseId)
                        .eq("user_id", UserContext.getUser())
        );
        //1. 该用户课表中没有该课程，返回null
        if (learningLesson == null) {
            return null;
        }

        //2. 查询该课程信息
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOS = courseClient.getSimpleInfoList(CollUtils.singletonList(courseId));
        if (CollUtils.isEmpty(courseSimpleInfoDTOS)) {
            return null;
        }
        CourseSimpleInfoDTO course = courseSimpleInfoDTOS.get(0);
        
        //2. 课程状态无效（待上架或已下架），返回null
        if (course.getStatus() == PENDING || course.getStatus() == REMOVED) {
            return null;
        }
        return learningLesson.getId();
    }

    /**
     * @param courseId
     * @return java.lang.Integer
     * @description: 根据课程id查询课程学习人数
     * @author: TanH
     * @date: 2025/12/26
     **/
    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        Integer count = baseMapper.selectCount(
                new QueryWrapper<LearningLesson>()
                        .eq("course_id", courseId)
                        .ne("status", LessonStatus.EXPIRED.getValue()) //查询未过期的课程
        );

        return count;
    }
}
