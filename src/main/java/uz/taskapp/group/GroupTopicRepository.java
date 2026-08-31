package uz.taskapp.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupTopicRepository extends JpaRepository<GroupTopicEntity, GroupTopicId> {
    List<GroupTopicEntity> findAllByIdGroupIdAndClosedFalseOrderByNameAsc(Long groupId);

    boolean existsByIdGroupIdAndIdTopicId(Long groupId, Long topicId);
}
