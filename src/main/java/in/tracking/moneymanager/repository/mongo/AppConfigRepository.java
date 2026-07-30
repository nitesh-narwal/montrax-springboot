package in.tracking.moneymanager.repository.mongo;

import in.tracking.moneymanager.document.AppConfigDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppConfigRepository extends MongoRepository<AppConfigDocument, String> {

    Optional<AppConfigDocument> findByConfigKey(String configKey);

    List<AppConfigDocument> findByCategory(String category);

    void deleteByConfigKey(String configKey);
}
