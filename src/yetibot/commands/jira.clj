(ns yetibot.commands.jira
  (:require
    [yetibot.observers.jira :refer [report-jira]]
    [yetibot.util :refer [filter-nil-vals map-to-strs]]
    [taoensso.timbre :refer [info warn error]]
    [yetibot.hooks :refer [cmd-hook]]
    [yetibot.api.jira :as api]))

(defn users-cmd
  "jira users # list the users for the configured project(s)"
  [_]
  (map :name (api/get-users)))

(defn resolve-cmd
  "jira resolve <issue> <comment> # resolve an issue and set its resolution to fixed"
  [{[_ iss comment] :match user :user}]
  (let [comment (format "%s: %s" (:name user) comment)]
    (if-let [issue-data (api/get-issue iss)]
      (let [resolved? (api/resolve-issue iss comment)
            ; refetch the issue data now that it's resolved
            issue-data (api/get-issue iss)
            formatted (api/format-issue issue-data)]
        (if resolved?
          formatted
          (into [(str "Unable to resolve issue " iss)] formatted)))
      (str "Unable to find any issues for " iss))))

(defn priorities-cmd
  "jira pri # list the priorities for this JIRA instance"
  [_]
  (->> (api/priorities)
       (map (juxt :name :description))
       flatten
       (apply sorted-map)))


; currently doesn't support more than one project key, but it could
(defn create-cmd
  "jira create <summary> # create issue with summary, unassigned
   jira create <summary> / <assignee> # create issue with summary and assignee
   jira create <summary> / <assignee> / <desc> # create issue with summary, assignee, and description"
  [{[_ summary assignee desc] :match}]
  (let [res (api/create-issue
              (filter-nil-vals {:summary summary
                                :assignee assignee
                                :desc desc}))]
    (if (re-find #"^2" (str (:status res) "2"))
      (let [iss-key (-> res :body :key)]
        (report-jira iss-key)
        (str "Created issue " iss-key))
      (map-to-strs (->> res :body :errors)))))

(defn recent-cmd
  "jira recent # show the 5 most recent issues"
  [_]
  (map api/format-issue-short
       (->> (api/recent)
           :body
           :issues
           (take 5))))

(cmd-hook #"jira"
          #"^recent" recent-cmd
          #"^pri" priorities-cmd
          #"^users" users-cmd
          #"^create\s+([^\/]+)\s+\/\s+([^\/]+)\s+\/\s+(.+)" create-cmd
          #"^create\s+([^\/]+)\s+\/\s+([^\/]+)" create-cmd
          #"^create\s+([^\/]+)" create-cmd
          #"^resolve\s+([\w\-]+)\s+(.+)" resolve-cmd)
