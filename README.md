# Social Stats App
As my final project for this class, I created an application that will
be later used in my company, mainly as an internal tool. We were frustrated that we have to keep
track of multiple social accounts at once, to get feedback on our Kickstarter campaign. Viewing
all accounts and checking responses or creating new content can be hard when you have
accounts across more than 10 platforms.

The main idea of this app is to connect all those accounts together, with single Android
application, which receives all needed data from our server, where we will be scraping/accessing
all necessary data and converting them to some common format (probably JSON). The app serves
as client counterpart.

## User documentation
Because of the reasons above, the application is made as simple to use as possible and consists
of three pages, described below.

### Main page
As we can see in the image below, the main page provides a simple overview of all tracked accounts in
the form of a panel per account. Each account panel (card) then contains basic information about total account
views and the number of posts. For more information about each account, you can tap these panels, which will
navigate you to the <strong>Account View</strong> page with detailed account statistics
over time and posts associated with that account.

![Local Image](./DocMedia/MainPage.png)

The update button triggers data synchronization (in our case, creating new testing data) and thus updating
existing stats, adding new posts or tracked accounts.

Since our goal is not only to display but also to create posts, we can achieve that via the floating plus button
in the bottom right corner of the page. If you tap this button from the main page, the
<strong>Create Post</strong> page will be preconfigured to associate the new post with all tracked accounts
(you can change it directly on the page, so nothing is set in stone). As we will see in description
of next page, you can also create explicitely for given account.

### Account View page
At the top of this page, you can see a bar chart showing the total number of the account views over time.
By default the visible range of the chart is limited to one week, but you can change the level of detail with
buttons placed above the chart. The chart supports the swipe gesture for changing the selected time on the
timeline, with the active date range shown above the chart.

If you want to see the exact value of the bar you can display it by tapping on the bar.

![Local Image](./DocMedia/accountpage.png)

Below the chart, you can see example of the post detail panel which displays basic information
about the post. Since the following distinction is critical, allow me to elaborate:
- <strong>Title</strong> at the top serves only for user as her own alias for the post (so that it is easier to find).
- <strong>Description</strong> below is the actual "text" of the post, which will be displayed on the social network.
- For description of other parameters, please refer to the technical documentation.

Posts are displayed in scrollable panel, ordered newest to oldest. In future version, I would like
to implement filtering methods, so you do not have to view all the post at once.

As mentioned before, you can create post specifically for the viewed account
via the floating plus button in the bottom right corner.

### Create Post page
As the name suggests, this page is all about creating new posts, and you only have to select account/s
and the content of your post. The account selection is realized with a drop down menu on top. For now, you can
select all accounts or a single account.

There's also a media picker, which will invoke system media picker where you can select one photo or video, etc.
Selecting the media and the title is required for the post to be created.

![Local Image](./DocMedia/createpost.png)

After you hit create, the new post is stored in the database. In the future version will be also uploaded
to our server.

## Tech documentation
This program is a simplification of the problem described above in the sense that I completely
omitted the server side of the utility by mocking all the necessary data. The application capabilities
are pretty similar to desired final state, but some details will be improved in the future based on
our own user experience.

### Data composition, generation and storage
Most of the popular social platforms have a common characteristics for posts which are media + text.
For our internal purposes and information value, I have decided to store few more values:

- <strong>Description</strong> is the text part of the actual post.
- <strong>Media</strong> is the URI of included media (video/image) of the post.
- <strong>Title</strong> serves as a shortcut for user to quickly identify the post.
- <strong>MediaType</strong> once again just for user to easily identify the type of used media.
- <strong>CreatedAt</strong> is the datetime of post creation (mainly for the user, but also for ordering)
- <strong>Views</strong> represent the total sum of the views associated with current post.

Each of those posts is then associated with specific account(s) (we store only the name of the
account for now).

All data is generated (mocked) in this version of the application, so we could test all it's functionalities independently of the server.
For that purpose there is the <strong>MockData</strong> class:

  - Generates new posts, and updates of post views.
  - Can generate post belonging to an account that is unknown to the application. In such case, it will
    create a new account, based on the name in the post.
  - For simplicity, the new accounts can come only from preselected array of account names.
  - Posts and accounts (post with unkown name) are created with certain probability
    (50% for post and 15% for new account) in attempt to match a real life scenario.
  - Otherwise each update generates a post update with new views on the post.

Also there's a <strong>MediaSeeder</strong> class:

  - Helper of the <strong>MockData</strong> class---it provides media files.
  - Since we mock all incoming data, I decided to store a few default media files directly in the
    project repository (app/assets/mock\_media) so when the application has it's own folder empty,
    we copy those images to the (emulated) device. That way I can provide you with better demonstration
    of application capabilities, because we use those media in newly generated mock posts.

### Bar chart
The visualisation of the statistics is realized via my company's <strong>BarChart</strong> class, which
I only slightly enhanced. Chart shows sums of view per day with changeable range of view (Day, Week,
Month, 6 Months, Year). Each of shown bars should be able to show precise amount of views after tap.

Because the original chart implementation did not contain support for scrolling/swiping, I decided to add this functionality
so that you can swipe left and right on the chart and shift the observed range by same amount in that
direction (swiping is limited not to move into the future).