package com.doctorexplorer.core.community.repo;

import com.doctorexplorer.core.community.domain.PostComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostComments extends JpaRepository<PostComment, UUID> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId);
}
