package com.doctorexplorer.core.community.repo;

import com.doctorexplorer.core.community.domain.GroupMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberships extends JpaRepository<GroupMembership, GroupMembership.Key> {

    List<GroupMembership> findByUserIdAndState(UUID userId, GroupMembership.State state);

    List<GroupMembership> findByGroupIdAndState(UUID groupId, GroupMembership.State state);

    List<GroupMembership> findByState(GroupMembership.State state);
}
