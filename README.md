# Introduction

The Discovery Artifact Manager is intended to facilitate testing, publishing,
and synchronization of Toolkit, discovery docs from API explorer, and discovery
based Google client libraries.

# Local machine setup

Install [git-subrepo](https://github.com/ingydotnet/git-subrepo) on your local machine.


# Adding a new client library repo

Use the `git subrepo clone` command, from the root directory of this repository. The NodeJS library, for example, is installed using:

``` shell
git subrepo clone https://github.com/google/google-api-nodejs-client.git clients/nodejs/google-api-nodejs-client
```

# Modifying a client library repo

To make changes to a repo, use the `git subrepo pull` and `git subrepo push` commands. The former will merge your local client with fetched upstream changes, and the latter will actually do the push to the upstream sub-repo. For example, to push the PHP client library:

``` shell
git subrepo pull clients/php/google-api-php-client-services
git subrepo push clients/php/google-api-php-client-services
```

During the course of your local work, you may find yourself deciding to reset your HEAD locally. If you do this after a subrepo push, trying to reset your HEAD to before the push, then this can cause some complications: you would not want `github subrepo` to subsequently pull again, as it normally does when pushing (since that will merge the upstream changes you pushed earlier). Instead, you can force-push your changes by using `git subrepo push --force`. We're still learning the quirks of `git subrepo`, but a good rule of thumb is to be extremely careful when manipulating references that have already been synced (push or pull) with the external subrepo locations.

After you push your subrepo, you should also push `discovery-artifact-manager` to your review branch.

# Pushing changes for review

When you make a change to code that lives in `discovery-artifact-manager`, either directly or via subrepos, you should stage your code to your own Github review branch and then create a Pull Request from there to the Github `master` branch.

1. Create a review branch on Github. We'll refer to the name of the branch as `${REVIEW_BRANCH}`.
1. Decide what local branch you'll push. Often, this will be master. We'll refer to this branch as `${LOCAL_BRANCH}`
1. From your local machine, push to the review branch:

```
git push origin ${LOCAL_BRANCH}:${REVIEW_BRANCH}
```

1. On Github, issue a Pull Request against the `master` branch.

